package com.example.vitruvianredux.data.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.example.vitruvianredux.data.ble.VitruvianBleManager
import com.example.vitruvianredux.domain.model.ConnectionState
import com.example.vitruvianredux.domain.model.WorkoutMetric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val logRepo = ConnectionLogRepository.instance

class AndroidBleRepository(
    private val context: Context
) : BleRepository {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val bleManager = VitruvianBleManager(context)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val scanner = bluetoothAdapter?.bluetoothLeScanner

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    override val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    override val metricsFlow: Flow<WorkoutMetric> = bleManager.metrics

    private val _handleState = MutableStateFlow(HandleState())
    override val handleState: StateFlow<HandleState> = _handleState.asStateFlow()

    // Rep events flow - will be populated when BLE manager parses rep notifications
    override val repEvents: Flow<RepNotification> = bleManager.repEvents

    private val foundDevicesMap = mutableMapOf<String, ScannedDevice>()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: result.scanRecord?.deviceName ?: return

            // Filter for Vitruvian devices
            if (name.startsWith("Vee_") || name.startsWith("VIT")) {
                val scannedDevice = ScannedDevice(
                    name = name,
                    address = device.address,
                    rssi = result.rssi
                )

                // Only log if this is a new device
                if (!foundDevicesMap.containsKey(device.address)) {
                    logRepo.info(
                        LogEventType.DEVICE_FOUND,
                        "Found Vitruvian device",
                        name,
                        device.address,
                        "RSSI: ${result.rssi} dBm"
                    )
                }

                foundDevicesMap[device.address] = scannedDevice
                _scannedDevices.value = foundDevicesMap.values.toList().sortedByDescending { it.rssi }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            logRepo.error(
                LogEventType.ERROR,
                "BLE scan failed",
                details = "Error code: $errorCode"
            )
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun startScanning() {
        if (bluetoothAdapter?.isEnabled == true) {
            foundDevicesMap.clear()
            _scannedDevices.value = emptyList()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            logRepo.info(LogEventType.SCAN_START, "Starting BLE scan for Vitruvian devices")
            scanner?.startScan(null, settings, scanCallback)
        } else {
            logRepo.warning(LogEventType.ERROR, "Cannot start scan - Bluetooth is disabled")
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopScanning() {
        if (bluetoothAdapter?.isEnabled == true) {
            scanner?.stopScan(scanCallback)
            logRepo.info(
                LogEventType.SCAN_STOP,
                "BLE scan stopped",
                details = "Found ${foundDevicesMap.size} Vitruvian device(s)"
            )
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(device: ScannedDevice) {
        stopScanning()
        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
        if (bluetoothDevice != null) {
            bleManager.connectToDevice(bluetoothDevice)
        }
    }

    override suspend fun disconnect() {
        bleManager.disconnectDevice()
    }

    override suspend fun setColorScheme(schemeIndex: Int) {
        // TODO: Construct color command
        // Command 0x??
    }

    override suspend fun sendWorkoutCommand(command: ByteArray) {
        bleManager.sendCommand(command)
    }

    override fun enableHandleDetection(enabled: Boolean) {
        // TODO: Implement handle detection logic
    }
}
