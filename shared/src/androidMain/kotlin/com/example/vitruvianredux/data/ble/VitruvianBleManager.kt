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

    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    // State
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _metrics = MutableSharedFlow<WorkoutMetric>(replay = 0)
    val metrics = _metrics.asSharedFlow()

    // Data parsing state
    private var previousPositionA = 0
    private var previousPositionB = 0
    private var lastPacketTime = 0L

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
                // Device is ready to use
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
            parsePacket(data)
        }
        enableNotifications(rxCharacteristic).enqueue()
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val service: BluetoothGattService? = gatt.getService(SERVICE_UUID)
        if (service != null) {
            txCharacteristic = service.getCharacteristic(TX_CHAR_UUID)
            rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID)
        }
        return txCharacteristic != null && rxCharacteristic != null
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

    private fun parsePacket(data: Data) {
        if (data.size() < 20) return // Basic length check

        // TODO: Implement full V1 protocol parsing
        // Based on previous knowledge:
        // Bytes 0-1: Header?
        // Bytes 2-3: Status?
        // ... positions and loads ...
        
        // Mocking parsing for now based on standard byte offsets typically found in these devices
        // This needs to be verified against the "official_logs" if we had them
        
        try {
            // Assuming Little Endian for these values
            val positionA = data.getIntValue(Data.FORMAT_SINT16, 4) ?: 0
            val positionB = data.getIntValue(Data.FORMAT_SINT16, 6) ?: 0
            val loadA = (data.getIntValue(Data.FORMAT_UINT16, 8) ?: 0) / 100f // Assuming scale factor
            val loadB = (data.getIntValue(Data.FORMAT_UINT16, 10) ?: 0) / 100f
            
            if (validateSample(positionA, positionB)) {
                val metric = WorkoutMetric(
                    timestamp = System.currentTimeMillis(),
                    loadA = loadA,
                    loadB = loadB,
                    positionA = positionA,
                    positionB = positionB,
                    status = 0
                )
                
                scope.launch {
                    _metrics.emit(metric)
                }
                
                previousPositionA = positionA
                previousPositionB = positionB
            }
        } catch (e: Exception) {
            // Parse error
        }
    }

    /**
     * Validates the sample to prevent spikes or invalid data.
     * FIX: Range increased to 30000 (3m) and allows negative values.
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
