package com.example.vitruvianredux.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import com.example.vitruvianredux.domain.model.ConnectionState
import com.example.vitruvianredux.domain.model.WorkoutMetric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver
import no.nordicsemi.android.ble.data.Data
import java.util.UUID
import kotlin.math.abs

/**
 * Nordic BLE Manager implementation for Vitruvian machines.
 * Handles the low-level GATT operations and protocol parsing.
 */
class VitruvianBleManager(
    context: Context
) : BleManager(context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    // Service and Characteristic UUIDs
    private val SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val TX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // Write
    private val RX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // Notify
    private val MONITOR_CHAR_UUID = UUID.fromString("90e991a6-c548-44ed-969b-eb541014eae3") // Read/Notify?
    private val PROPERTY_CHAR_UUID = UUID.fromString("5fa538ec-d041-42f6-bbd6-c30d475387b7") // Read

    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var monitorCharacteristic: BluetoothGattCharacteristic? = null
    private var propertyCharacteristic: BluetoothGattCharacteristic? = null

    // State
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _metrics = MutableSharedFlow<WorkoutMetric>(replay = 0)
    val metrics = _metrics.asSharedFlow()

    // Data parsing state
    private var previousPositionA = 0
    private var previousPositionB = 0
    private var lastPacketTime = 0L
    private var lastGoodPosA = 0
    private var lastGoodPosB = 0

    init {
        connectionObserver = object : ConnectionObserver {
            override fun onDeviceConnecting(device: BluetoothDevice) {
                _connectionState.value = ConnectionState.Connecting
            }

            override fun onDeviceConnected(device: BluetoothDevice) {
                _connectionState.value = ConnectionState.Connected(device.name ?: "Unknown", device.address)
            }

            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                _connectionState.value = ConnectionState.Error("Failed to connect: $reason")
            }

            override fun onDeviceReady(device: BluetoothDevice) {
                // Device is ready to use, start heartbeat
                startHeartbeat()
            }

            override fun onDeviceDisconnecting(device: BluetoothDevice) {
                // Disconnecting
            }

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    override fun initialize() {
        requestMtu(512).enqueue()
        
        setNotificationCallback(rxCharacteristic).with { device, data ->
            // Keep RX parsing for command responses or legacy notifications
             parsePacket(data) 
        }
        enableNotifications(rxCharacteristic).enqueue()
    }

    private fun startHeartbeat() {
        scope.launch {
            while (_connectionState.value is ConnectionState.Connected) {
                // Poll monitor characteristic every 100ms (Heartbeat + Data)
                if (monitorCharacteristic != null) {
                    readCharacteristic(monitorCharacteristic)
                        .with { _, data -> parseMonitorData(data) }
                        .enqueue()
                }
                
                // Poll property characteristic every 500ms (Secondary Heartbeat)
                // We can skip this if monitor is enough, but let's match the reference app roughly
                // To avoid congestion, we'll just do monitor mostly.
                
                kotlinx.coroutines.delay(100)
            }
        }
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val service: BluetoothGattService? = gatt.getService(SERVICE_UUID)
        if (service != null) {
            txCharacteristic = service.getCharacteristic(TX_CHAR_UUID)
            rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID)
            monitorCharacteristic = service.getCharacteristic(MONITOR_CHAR_UUID)
            propertyCharacteristic = service.getCharacteristic(PROPERTY_CHAR_UUID)
        }
        
        // If monitor is not in the main service, try to find it globally (unlikely but safe)
        if (monitorCharacteristic == null) {
             gatt.services.forEach { s ->
                 val m = s.getCharacteristic(MONITOR_CHAR_UUID)
                 if (m != null) monitorCharacteristic = m
             }
        }

        return txCharacteristic != null && rxCharacteristic != null 
        // We don't strictly require monitor/property to return true, 
        // to maintain backward compatibility if headers change, but it's needed for heartbeat.
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        connect(device)
            .retry(3, 100)
            .useAutoConnect(false)
            .enqueue()
    }

    fun disconnectDevice() {
        disconnect().enqueue()
    }

    fun sendCommand(command: ByteArray) {
        writeCharacteristic(txCharacteristic, command, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            .enqueue()
    }

    private fun parseMonitorData(data: Data) {
        if (data.size() < 16) return

        try {
            // JS Reference:
            // f0 (0) = ticks low
            // f1 (2) = ticks high
            // f2 (4) = PosA
            // f4 (8) = LoadA
            // f5 (10) = PosB
            // f7 (14) = LoadB

            val ticks = data.getIntValue(Data.FORMAT_UINT32, 0) ?: 0
            
            // Position: UInt16 at 4 and 10
            var posA = data.getIntValue(Data.FORMAT_UINT16, 4) ?: 0
            var posB = data.getIntValue(Data.FORMAT_UINT16, 10) ?: 0
            
            // Filter spikes (> 50000)
            if (posA > 50000) posA = lastGoodPosA else lastGoodPosA = posA
            if (posB > 50000) posB = lastGoodPosB else lastGoodPosB = posB

            // Load: UInt16 at 8 and 14, divide by 100.0
            val loadARaw = data.getIntValue(Data.FORMAT_UINT16, 8) ?: 0
            val loadBRaw = data.getIntValue(Data.FORMAT_UINT16, 14) ?: 0
            
            val loadA = loadARaw / 100f
            val loadB = loadBRaw / 100f

            val metric = WorkoutMetric(
                timestamp = System.currentTimeMillis(), // Or use ticks?
                loadA = loadA,
                loadB = loadB,
                positionA = posA,
                positionB = posB,
                status = 0 // Status not in this packet?
            )
            
            scope.launch {
                _metrics.emit(metric)
            }
            
            previousPositionA = posA
            previousPositionB = posB

        } catch (e: Exception) {
            // Parse error
        }
    }

    private fun parsePacket(data: Data) {
        // Keep legacy parsing or command response handling here
        // The original logic was likely guessing offsets, so we rely on parseMonitorData now.
    }

    /**
     * Validates the sample to prevent spikes or invalid data.
     * Legacy validation - mostly replaced by parseMonitorData logic
     */
    private fun validateSample(posA: Int, posB: Int): Boolean {
        // Check if values are within physical bounds (approx 3 meters max extension)
        // Allowed range: -30000 to 30000 mm (30 meters? No, likely 3000mm = 3m)
        // The context says "30000 (3m)", so units might be 0.1mm or the protocol uses a large range.
        if (abs(posA) > 30000 || abs(posB) > 30000) {
            return false
        }
        
        // Check for impossible jumps (spikes)
        // If this is the first sample, don't check drift
        if (previousPositionA == 0 && previousPositionB == 0) return true
        
        val driftA = abs(posA - previousPositionA)
        val driftB = abs(posB - previousPositionB)
        
        // Max realistic movement per sample (e.g., 100ms) is probably < 50cm (5000 units)
        if (driftA > 5000 || driftB > 5000) {
            return false
        }
        
        return true
    }
}
