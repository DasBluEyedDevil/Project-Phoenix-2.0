@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.example.vitruvianredux.data.repository

import com.example.vitruvianredux.domain.model.ConnectionState
import com.example.vitruvianredux.domain.model.WorkoutMetric
import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.CoreBluetooth.*
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.darwin.NSObject
import platform.posix.memcpy
import co.touchlab.kermit.Logger

/**
 * iOS BLE Repository implementation using CoreBluetooth.
 *
 * Connects to Vitruvian Trainer machines using Nordic UART Service (NUS).
 * Device names typically start with "Vee_" (V-Form) or "VIT" (Trainer+).
 */
class IosBleRepository : BleRepository {

    private val log = Logger.withTag("IosBleRepository")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Nordic UART Service UUIDs
    companion object {
        private const val NUS_SERVICE_UUID_STRING = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
        private const val NUS_TX_UUID_STRING = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
        private const val NUS_RX_UUID_STRING = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
    }

    private val NUS_SERVICE_UUID = CBUUID.UUIDWithString(NUS_SERVICE_UUID_STRING)
    private val NUS_TX_UUID = CBUUID.UUIDWithString(NUS_TX_UUID_STRING)
    private val NUS_RX_UUID = CBUUID.UUIDWithString(NUS_RX_UUID_STRING)

    // State flows
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _handleState = MutableStateFlow(HandleState())
    override val handleState: StateFlow<HandleState> = _handleState.asStateFlow()

    private val _metricsFlow = MutableSharedFlow<WorkoutMetric>(replay = 1)
    override val metricsFlow: Flow<WorkoutMetric> = _metricsFlow.asSharedFlow()

    private val _repEvents = MutableSharedFlow<RepNotification>()
    override val repEvents: Flow<RepNotification> = _repEvents.asSharedFlow()

    // CoreBluetooth objects
    private var centralManager: CBCentralManager? = null
    private var connectedPeripheral: CBPeripheral? = null
    private var txCharacteristic: CBCharacteristic? = null
    private var rxCharacteristic: CBCharacteristic? = null

    // Connected device info
    private var connectedDeviceName: String = ""
    private var connectedDeviceAddress: String = ""

    // Handle detection
    private var handleDetectionEnabled = false

    // Discovered peripherals map
    private val discoveredPeripherals = mutableMapOf<String, CBPeripheral>()

    // Delegate holder to prevent garbage collection
    private val centralDelegate: CentralManagerDelegate
    private val peripheralDelegate: PeripheralDelegate

    init {
        centralDelegate = CentralManagerDelegate(this)
        peripheralDelegate = PeripheralDelegate(this)
        centralManager = CBCentralManager(centralDelegate, null)
    }

    internal fun onCentralStateUpdated(state: Long) {
        log.d { "Central manager state updated: $state" }
        when (state) {
            CBManagerStatePoweredOn -> log.i { "Bluetooth is powered on" }
            CBManagerStatePoweredOff -> {
                log.w { "Bluetooth is powered off" }
                _connectionState.value = ConnectionState.Disconnected
            }
            CBManagerStateUnauthorized -> {
                log.e { "Bluetooth unauthorized" }
                _connectionState.value = ConnectionState.Error("Bluetooth access not authorized")
            }
            CBManagerStateUnsupported -> {
                log.e { "Bluetooth unsupported" }
                _connectionState.value = ConnectionState.Error("Bluetooth not supported")
            }
        }
    }

    internal fun onPeripheralDiscovered(
        peripheral: CBPeripheral,
        @Suppress("UNUSED_PARAMETER") advertisementData: Map<Any?, *>,
        rssi: Int
    ) {
        val name = peripheral.name ?: return
        val identifier = peripheral.identifier.UUIDString

        // Only accept Vitruvian devices
        if (!name.startsWith("Vee_") && !name.startsWith("VIT")) {
            return
        }

        log.d { "Discovered device: $name ($identifier) RSSI: $rssi" }

        // Store peripheral reference
        discoveredPeripherals[identifier] = peripheral

        // Update scanned devices list
        val device = ScannedDevice(name = name, address = identifier, rssi = rssi)
        val currentDevices = _scannedDevices.value.toMutableList()
        val existingIndex = currentDevices.indexOfFirst { it.address == identifier }
        if (existingIndex >= 0) {
            currentDevices[existingIndex] = device
        } else {
            currentDevices.add(device)
        }
        _scannedDevices.value = currentDevices
    }

    internal fun onPeripheralConnected(peripheral: CBPeripheral) {
        log.i { "Connected to peripheral: ${peripheral.name}" }
        connectedPeripheral = peripheral
        connectedDeviceName = peripheral.name ?: "Unknown"
        connectedDeviceAddress = peripheral.identifier.UUIDString
        _connectionState.value = ConnectionState.Connected(
            deviceName = connectedDeviceName,
            deviceAddress = connectedDeviceAddress
        )
        peripheral.delegate = peripheralDelegate
        peripheral.discoverServices(listOf(NUS_SERVICE_UUID))
    }

    internal fun onPeripheralConnectionFailed(@Suppress("UNUSED_PARAMETER") peripheral: CBPeripheral, error: NSError?) {
        log.e { "Failed to connect: ${error?.localizedDescription}" }
        _connectionState.value = ConnectionState.Error(error?.localizedDescription ?: "Connection failed")
    }

    internal fun onPeripheralDisconnected(peripheral: CBPeripheral, @Suppress("UNUSED_PARAMETER") error: NSError?) {
        log.i { "Disconnected from peripheral: ${peripheral.name}" }
        connectedPeripheral = null
        txCharacteristic = null
        rxCharacteristic = null
        connectedDeviceName = ""
        connectedDeviceAddress = ""
        _connectionState.value = ConnectionState.Disconnected
    }

    internal fun onServicesDiscovered(peripheral: CBPeripheral, error: NSError?) {
        if (error != null) {
            log.e { "Error discovering services: ${error.localizedDescription}" }
            return
        }

        peripheral.services?.forEach { service ->
            val cbService = service as CBService
            if (cbService.UUID.UUIDString.lowercase() == NUS_SERVICE_UUID_STRING) {
                log.d { "Found NUS service, discovering characteristics" }
                peripheral.discoverCharacteristics(listOf(NUS_TX_UUID, NUS_RX_UUID), cbService)
            }
        }
    }

    internal fun onCharacteristicsDiscovered(service: CBService, peripheral: CBPeripheral, error: NSError?) {
        if (error != null) {
            log.e { "Error discovering characteristics: ${error.localizedDescription}" }
            return
        }

        service.characteristics?.forEach { char ->
            val characteristic = char as CBCharacteristic
            val uuidString = characteristic.UUID.UUIDString.lowercase()
            when (uuidString) {
                NUS_TX_UUID_STRING -> {
                    log.d { "Found TX characteristic" }
                    txCharacteristic = characteristic
                }
                NUS_RX_UUID_STRING -> {
                    log.d { "Found RX characteristic, enabling notifications" }
                    rxCharacteristic = characteristic
                    peripheral.setNotifyValue(true, characteristic)
                }
            }
        }
    }

    internal fun onCharacteristicValueUpdated(characteristic: CBCharacteristic, error: NSError?) {
        if (error != null) {
            log.e { "Error reading characteristic: ${error.localizedDescription}" }
            return
        }

        val data = characteristic.value ?: return
        processIncomingData(data.toByteArray())
    }

    override suspend fun startScanning() {
        log.i { "Starting BLE scan" }
        _scannedDevices.value = emptyList()
        discoveredPeripherals.clear()

        centralManager?.scanForPeripheralsWithServices(
            serviceUUIDs = listOf(NUS_SERVICE_UUID),
            options = mapOf(CBCentralManagerScanOptionAllowDuplicatesKey to true)
        )
        _connectionState.value = ConnectionState.Scanning
    }

    override suspend fun stopScanning() {
        log.i { "Stopping BLE scan" }
        centralManager?.stopScan()
        if (_connectionState.value == ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    override suspend fun connect(device: ScannedDevice) {
        log.i { "Connecting to device: ${device.name}" }
        _connectionState.value = ConnectionState.Connecting

        val peripheral = discoveredPeripherals[device.address]
        if (peripheral == null) {
            log.e { "Peripheral not found for address: ${device.address}" }
            _connectionState.value = ConnectionState.Error("Device not found")
            return
        }

        centralManager?.connectPeripheral(peripheral, options = null)
    }

    override suspend fun disconnect() {
        log.i { "Disconnecting" }
        connectedPeripheral?.let { peripheral ->
            centralManager?.cancelPeripheralConnection(peripheral)
        }
    }

    override suspend fun setColorScheme(schemeIndex: Int) {
        log.d { "Setting color scheme: $schemeIndex" }
        // Color scheme command - implementation depends on machine protocol
    }

    override suspend fun sendWorkoutCommand(command: ByteArray) {
        val tx = txCharacteristic ?: run {
            log.w { "TX characteristic not available" }
            return
        }
        val peripheral = connectedPeripheral ?: run {
            log.w { "Not connected to peripheral" }
            return
        }

        val nsData = command.toNSData()
        peripheral.writeValue(nsData, tx, CBCharacteristicWriteWithResponse)
    }

    override fun enableHandleDetection(enabled: Boolean) {
        handleDetectionEnabled = enabled
        log.d { "Handle detection enabled: $enabled" }
    }

    private fun processIncomingData(data: ByteArray) {
        if (data.size < 2) return

        when (data[0].toInt() and 0xFF) {
            0x01 -> parseMetricsPacket(data)
            0x02 -> parseRepNotification(data)
        }
    }

    private fun parseMetricsPacket(data: ByteArray) {
        if (data.size < 16) return

        try {
            val positionA = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
            val positionB = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
            val loadA = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
            val loadB = ((data[8].toInt() and 0xFF) shl 8) or (data[9].toInt() and 0xFF)
            val velocityA = ((data[10].toInt() and 0xFF) shl 8) or (data[11].toInt() and 0xFF)
            val velocityB = ((data[12].toInt() and 0xFF) shl 8) or (data[13].toInt() and 0xFF)

            val metric = WorkoutMetric(
                loadA = loadA / 10f,
                loadB = loadB / 10f,
                positionA = positionA,
                positionB = positionB,
                velocityA = (velocityA - 32768).toDouble(),
                velocityB = (velocityB - 32768).toDouble()
            )

            scope.launch { _metricsFlow.emit(metric) }

            if (handleDetectionEnabled) {
                val activeThreshold = 500
                val leftDetected = positionA > activeThreshold
                val rightDetected = positionB > activeThreshold
                val currentState = _handleState.value
                if (currentState.leftDetected != leftDetected || currentState.rightDetected != rightDetected) {
                    _handleState.value = HandleState(leftDetected, rightDetected)
                }
            }
        } catch (e: Exception) {
            log.e { "Error parsing metrics: ${e.message}" }
        }
    }

    private fun parseRepNotification(data: ByteArray) {
        if (data.size < 5) return

        try {
            val notification = RepNotification(
                topCounter = data[1].toInt() and 0xFF,
                completeCounter = data[2].toInt() and 0xFF,
                repsRomCount = data[3].toInt() and 0xFF,
                repsSetCount = data[4].toInt() and 0xFF
            )
            scope.launch { _repEvents.emit(notification) }
        } catch (e: Exception) {
            log.e { "Error parsing rep notification: ${e.message}" }
        }
    }
}

/**
 * CBCentralManager delegate implementation
 */
private class CentralManagerDelegate(
    private val repository: IosBleRepository
) : NSObject(), CBCentralManagerDelegateProtocol {

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        repository.onCentralStateUpdated(central.state)
    }

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber
    ) {
        repository.onPeripheralDiscovered(didDiscoverPeripheral, advertisementData, RSSI.intValue)
    }

    override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
        repository.onPeripheralConnected(didConnectPeripheral)
    }

    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didFailToConnectPeripheral: CBPeripheral,
        error: NSError?
    ) {
        repository.onPeripheralConnectionFailed(didFailToConnectPeripheral, error)
    }

    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        error: NSError?
    ) {
        repository.onPeripheralDisconnected(didDisconnectPeripheral, error)
    }
}

/**
 * CBPeripheral delegate implementation
 */
private class PeripheralDelegate(
    private val repository: IosBleRepository
) : NSObject(), CBPeripheralDelegateProtocol {

    override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
        repository.onServicesDiscovered(peripheral, didDiscoverServices)
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverCharacteristicsForService: CBService,
        error: NSError?
    ) {
        repository.onCharacteristicsDiscovered(didDiscoverCharacteristicsForService, peripheral, error)
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateValueForCharacteristic: CBCharacteristic,
        error: NSError?
    ) {
        repository.onCharacteristicValueUpdated(didUpdateValueForCharacteristic, error)
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didWriteValueForCharacteristic: CBCharacteristic,
        error: NSError?
    ) {
        // Write complete callback - can log errors if needed
    }
}

// Extension functions for NSData <-> ByteArray conversion
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)

    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, length.toULong())
    }
    return bytes
}

private fun ByteArray.toNSData(): NSData {
    if (this.isEmpty()) return NSData()

    return memScoped {
        NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong())
    }
}
