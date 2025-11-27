package com.example.vitruvianredux.data.repository

import com.example.vitruvianredux.domain.model.ConnectionState
import com.example.vitruvianredux.domain.model.WorkoutMetric
import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Kable-based BLE Repository implementation for Vitruvian machines.
 * Uses Kotlin Multiplatform Kable library for unified BLE across all platforms.
 */
@OptIn(ExperimentalUuidApi::class)
class KableBleRepository : BleRepository {

    private val log = Logger.withTag("KableBleRepository")
    private val logRepo = ConnectionLogRepository.instance
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Nordic UART Service UUIDs
    companion object {
        private val NUS_SERVICE_UUID = Uuid.parse("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        private val NUS_TX_UUID = Uuid.parse("6e400002-b5a3-f393-e0a9-e50e24dcca9e")  // Write
        private val NUS_RX_UUID = Uuid.parse("6e400003-b5a3-f393-e0a9-e50e24dcca9e")  // Notify
        private val MONITOR_UUID = Uuid.parse("90e991a6-c548-44ed-969b-eb541014eae3") // Read/Notify

        // Connection settings
        private const val CONNECTION_RETRY_COUNT = 3
        private const val CONNECTION_RETRY_DELAY_MS = 100L
        private const val DESIRED_MTU = 512
    }

    // Kable characteristic references
    private val txCharacteristic = characteristicOf(
        service = NUS_SERVICE_UUID,
        characteristic = NUS_TX_UUID
    )
    private val rxCharacteristic = characteristicOf(
        service = NUS_SERVICE_UUID,
        characteristic = NUS_RX_UUID
    )
    private val monitorCharacteristic = characteristicOf(
        service = NUS_SERVICE_UUID,
        characteristic = MONITOR_UUID
    )

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

    // Kable objects
    private var peripheral: Peripheral? = null
    private val discoveredAdvertisements = mutableMapOf<String, Advertisement>()
    private var scanJob: kotlinx.coroutines.Job? = null

    // Handle detection
    private var handleDetectionEnabled = false

    // Connected device info (for logging)
    private var connectedDeviceName: String = ""
    private var connectedDeviceAddress: String = ""

    // Data parsing state
    private var lastGoodPosA = 0
    private var lastGoodPosB = 0

    override suspend fun startScanning() {
        log.i { "Starting BLE scan for Vitruvian devices" }
        logRepo.info(LogEventType.SCAN_START, "Starting BLE scan for Vitruvian devices")

        _scannedDevices.value = emptyList()
        discoveredAdvertisements.clear()
        _connectionState.value = ConnectionState.Scanning

        scanJob = Scanner {
            // No specific filters - we'll filter by name
        }
            .advertisements
            .filter { advertisement ->
                val name = advertisement.name ?: return@filter false
                name.startsWith("Vee_") || name.startsWith("VIT")
            }
            .onEach { advertisement ->
                val name = advertisement.name ?: return@onEach
                val identifier = advertisement.identifier.toString()

                // Only log if this is a new device
                if (!discoveredAdvertisements.containsKey(identifier)) {
                    log.d { "Discovered device: $name ($identifier) RSSI: ${advertisement.rssi}" }
                    logRepo.info(
                        LogEventType.DEVICE_FOUND,
                        "Found Vitruvian device",
                        name,
                        identifier,
                        "RSSI: ${advertisement.rssi} dBm"
                    )
                }

                // Store advertisement reference
                discoveredAdvertisements[identifier] = advertisement

                // Update scanned devices list
                val device = ScannedDevice(
                    name = name,
                    address = identifier,
                    rssi = advertisement.rssi
                )
                val currentDevices = _scannedDevices.value.toMutableList()
                val existingIndex = currentDevices.indexOfFirst { it.address == identifier }
                if (existingIndex >= 0) {
                    currentDevices[existingIndex] = device
                } else {
                    currentDevices.add(device)
                }
                _scannedDevices.value = currentDevices.sortedByDescending { it.rssi }
            }
            .catch { e ->
                log.e { "Scan error: ${e.message}" }
                logRepo.error(LogEventType.ERROR, "BLE scan failed", details = e.message)
                _connectionState.value = ConnectionState.Error("Scan failed: ${e.message}")
            }
            .launchIn(scope)
    }

    override suspend fun stopScanning() {
        log.i { "Stopping BLE scan" }
        logRepo.info(
            LogEventType.SCAN_STOP,
            "BLE scan stopped",
            details = "Found ${discoveredAdvertisements.size} Vitruvian device(s)"
        )
        scanJob?.cancel()
        scanJob = null
        if (_connectionState.value == ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    override suspend fun connect(device: ScannedDevice) {
        log.i { "Connecting to device: ${device.name}" }
        logRepo.info(
            LogEventType.CONNECT_START,
            "Connecting to device",
            device.name,
            device.address
        )
        _connectionState.value = ConnectionState.Connecting

        val advertisement = discoveredAdvertisements[device.address]
        if (advertisement == null) {
            log.e { "Advertisement not found for device: ${device.address}" }
            logRepo.error(
                LogEventType.CONNECT_FAIL,
                "Device not found in scanned list",
                device.name,
                device.address
            )
            _connectionState.value = ConnectionState.Error("Device not found")
            return
        }

        // Store device info for logging
        connectedDeviceName = device.name
        connectedDeviceAddress = device.address

        try {
            stopScanning()

            peripheral = Peripheral(advertisement)

            // Observe connection state
            peripheral?.state
                ?.onEach { state ->
                    when (state) {
                        is State.Connecting -> {
                            _connectionState.value = ConnectionState.Connecting
                        }
                        is State.Connected -> {
                            logRepo.info(
                                LogEventType.CONNECT_SUCCESS,
                                "Device connected successfully",
                                connectedDeviceName,
                                connectedDeviceAddress
                            )
                            _connectionState.value = ConnectionState.Connected(
                                deviceName = device.name,
                                deviceAddress = device.address
                            )
                            // Launch onDeviceReady in a coroutine since we're in a non-suspend context
                            scope.launch { onDeviceReady() }
                        }
                        is State.Disconnecting -> {
                            log.d { "Disconnecting from device" }
                            logRepo.info(
                                LogEventType.DISCONNECT,
                                "Device disconnecting",
                                connectedDeviceName,
                                connectedDeviceAddress
                            )
                        }
                        is State.Disconnected -> {
                            logRepo.info(
                                LogEventType.DISCONNECT,
                                "Device disconnected",
                                connectedDeviceName,
                                connectedDeviceAddress
                            )
                            _connectionState.value = ConnectionState.Disconnected
                            peripheral = null
                            connectedDeviceName = ""
                            connectedDeviceAddress = ""
                        }
                    }
                }
                ?.launchIn(scope)

            // Connection with retry logic
            var lastException: Exception? = null
            for (attempt in 1..CONNECTION_RETRY_COUNT) {
                try {
                    log.d { "Connection attempt $attempt of $CONNECTION_RETRY_COUNT" }
                    peripheral?.connect()
                    log.i { "Connection initiated to ${device.name}" }
                    return // Success, exit retry loop
                } catch (e: Exception) {
                    lastException = e
                    log.w { "Connection attempt $attempt failed: ${e.message}" }
                    if (attempt < CONNECTION_RETRY_COUNT) {
                        delay(CONNECTION_RETRY_DELAY_MS)
                    }
                }
            }

            // All retries failed
            throw lastException ?: Exception("Connection failed after $CONNECTION_RETRY_COUNT attempts")

        } catch (e: Exception) {
            log.e { "Connection failed: ${e.message}" }
            logRepo.error(
                LogEventType.CONNECT_FAIL,
                "Failed to connect to device",
                device.name,
                device.address,
                e.message
            )
            _connectionState.value = ConnectionState.Error("Connection failed: ${e.message}")
            peripheral = null
            connectedDeviceName = ""
            connectedDeviceAddress = ""
        }
    }

    /**
     * Called when the device is connected and ready.
     * Requests MTU and starts observing notifications.
     */
    private suspend fun onDeviceReady() {
        val p = peripheral ?: return

        // Request MTU
        try {
            logRepo.debug(LogEventType.MTU_CHANGED, "Requesting MTU $DESIRED_MTU")
            // Note: Kable handles MTU negotiation automatically, but we can request a specific size
            // The actual MTU may be different depending on the device
        } catch (e: Exception) {
            log.w { "MTU request failed: ${e.message}" }
        }

        logRepo.info(
            LogEventType.SERVICE_DISCOVERED,
            "Device ready, starting notifications",
            connectedDeviceName,
            connectedDeviceAddress
        )

        startObservingNotifications()
    }

    private fun startObservingNotifications() {
        val p = peripheral ?: return

        logRepo.info(
            LogEventType.NOTIFICATION,
            "Enabling RX notifications",
            connectedDeviceName,
            connectedDeviceAddress
        )

        // Observe RX characteristic for notifications
        scope.launch {
            try {
                p.observe(rxCharacteristic)
                    .catch { e ->
                        log.e { "RX observation error: ${e.message}" }
                        logRepo.error(
                            LogEventType.ERROR,
                            "RX notification error",
                            connectedDeviceName,
                            connectedDeviceAddress,
                            e.message
                        )
                    }
                    .collect { data ->
                        logRepo.debug(
                            LogEventType.NOTIFICATION,
                            "RX notification received",
                            details = "Size: ${data.size} bytes"
                        )
                        processIncomingData(data)
                    }
            } catch (e: Exception) {
                log.e { "Failed to observe RX: ${e.message}" }
                logRepo.error(
                    LogEventType.ERROR,
                    "Failed to enable RX notifications",
                    connectedDeviceName,
                    connectedDeviceAddress,
                    e.message
                )
            }
        }

        // Poll monitor characteristic for real-time metrics (heartbeat)
        scope.launch {
            try {
                while (_connectionState.value is ConnectionState.Connected) {
                    try {
                        val data = p.read(monitorCharacteristic)
                        parseMonitorData(data)
                    } catch (e: Exception) {
                        // Monitor characteristic might not be available on all devices
                        log.d { "Monitor read failed: ${e.message}" }
                    }
                    delay(100)
                }
            } catch (e: Exception) {
                log.e { "Monitor polling stopped: ${e.message}" }
            }
        }
    }

    override suspend fun disconnect() {
        log.i { "Disconnecting" }
        try {
            peripheral?.disconnect()
        } catch (e: Exception) {
            log.e { "Disconnect error: ${e.message}" }
        }
        peripheral = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun setColorScheme(schemeIndex: Int) {
        log.d { "Setting color scheme: $schemeIndex" }
        // Color scheme command - implementation depends on machine protocol
    }

    override suspend fun sendWorkoutCommand(command: ByteArray) {
        val p = peripheral
        if (p == null) {
            log.w { "Not connected - cannot send command" }
            logRepo.warning(
                LogEventType.ERROR,
                "Cannot send command - not connected"
            )
            return
        }

        try {
            // Use WriteType.WithoutResponse for faster writes (matches Nordic behavior)
            p.write(txCharacteristic, command, WriteType.WithoutResponse)
            log.d { "Command sent: ${command.size} bytes" }
            logRepo.debug(
                LogEventType.COMMAND_SENT,
                "Sending command",
                connectedDeviceName,
                connectedDeviceAddress,
                "Size: ${command.size} bytes, Data: ${command.joinToString(" ") { it.toHexString() }}"
            )
        } catch (e: Exception) {
            log.e { "Failed to send command: ${e.message}" }
            logRepo.error(
                LogEventType.ERROR,
                "Failed to send command",
                connectedDeviceName,
                connectedDeviceAddress,
                e.message
            )
        }
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

    private fun parseMonitorData(data: ByteArray) {
        if (data.size < 16) return

        try {
            // Monitor characteristic data parsing
            // f0 (0) = ticks low
            // f1 (2) = ticks high
            // f2 (4) = PosA
            // f4 (8) = LoadA
            // f5 (10) = PosB
            // f7 (14) = LoadB

            var posA = getUInt16(data, 4)
            var posB = getUInt16(data, 10)

            // Filter spikes (> 50000)
            if (posA > 50000) posA = lastGoodPosA else lastGoodPosA = posA
            if (posB > 50000) posB = lastGoodPosB else lastGoodPosB = posB

            val loadARaw = getUInt16(data, 8)
            val loadBRaw = getUInt16(data, 14)

            val loadA = loadARaw / 100f
            val loadB = loadBRaw / 100f

            val metric = WorkoutMetric(
                loadA = loadA,
                loadB = loadB,
                positionA = posA,
                positionB = posB
            )

            scope.launch { _metricsFlow.emit(metric) }

            if (handleDetectionEnabled) {
                val activeThreshold = 500
                val leftDetected = posA > activeThreshold
                val rightDetected = posB > activeThreshold
                val currentState = _handleState.value
                if (currentState.leftDetected != leftDetected || currentState.rightDetected != rightDetected) {
                    _handleState.value = HandleState(leftDetected, rightDetected)
                }
            }

        } catch (e: Exception) {
            log.e { "Error parsing monitor data: ${e.message}" }
        }
    }

    private fun parseMetricsPacket(data: ByteArray) {
        if (data.size < 16) return

        try {
            val positionA = getUInt16(data, 2)
            val positionB = getUInt16(data, 4)
            val loadA = getUInt16(data, 6)
            val loadB = getUInt16(data, 8)
            val velocityA = getUInt16(data, 10)
            val velocityB = getUInt16(data, 12)

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

    private fun getUInt16(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    /**
     * Convert a Byte to a two-character uppercase hex string (KMP-compatible).
     */
    private fun Byte.toHexString(): String {
        val hex = "0123456789ABCDEF"
        val value = this.toInt() and 0xFF
        return "${hex[value shr 4]}${hex[value and 0x0F]}"
    }
}
