package com.example.vitruvianredux.data.repository

import com.example.vitruvianredux.domain.model.ConnectionState
import com.example.vitruvianredux.domain.model.WorkoutMetric
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Scanned BLE device
 */
data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int = 0
)

/**
 * Handle detection state
 */
data class HandleState(
    val leftDetected: Boolean = false,
    val rightDetected: Boolean = false
)

/**
 * Auto-stop UI state for Just Lift mode
 */
data class AutoStopUiState(
    val isActive: Boolean = false,
    val secondsRemaining: Int = 0,
    val progress: Float = 0f
)

/**
 * Handle activity state for auto-start/auto-stop logic.
 * Tracks the workout phase based on handle position.
 */
enum class HandleActivityState {
    /** Waiting for user to pick up handles (at rest position) */
    WaitingForRest,
    /** Handles are lifted and user is actively working */
    Active,
    /** Set completed, waiting for rest timer or next action */
    SetComplete
}

/**
 * Rep notification from the Vitruvian machine.
 * The machine sends rep events with counters that track:
 * - topCounter: increments when user reaches top of movement (concentric peak)
 * - completeCounter: increments when user completes the rep (eccentric valley)
 * - repsRomCount: machine's warmup rep count
 * - repsSetCount: machine's working set rep count
 */
data class RepNotification(
    val topCounter: Int,
    val completeCounter: Int,
    val repsRomCount: Int,
    val repsSetCount: Int
)

/**
 * BLE Repository interface - platform-specific implementation required
 * TODO: Implement expect/actual for Android (Nordic BLE) and other platforms
 */
interface BleRepository {
    val connectionState: StateFlow<ConnectionState>
    val metricsFlow: Flow<WorkoutMetric>
    val scannedDevices: StateFlow<List<ScannedDevice>>
    val handleState: StateFlow<HandleState>
    val repEvents: Flow<RepNotification>

    suspend fun startScanning()
    suspend fun stopScanning()
    suspend fun connect(device: ScannedDevice)
    suspend fun disconnect()
    suspend fun setColorScheme(schemeIndex: Int)
    suspend fun sendWorkoutCommand(command: ByteArray)

    // Handle detection
    fun enableHandleDetection(enabled: Boolean)
}

/**
 * Stub BLE Repository for compilation - does nothing
 */
class StubBleRepository : BleRepository {
    override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val metricsFlow: Flow<WorkoutMetric> = MutableStateFlow(
        WorkoutMetric(loadA = 0f, loadB = 0f, positionA = 0, positionB = 0)
    )
    override val scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    override val handleState = MutableStateFlow(HandleState())
    override val repEvents: Flow<RepNotification> = kotlinx.coroutines.flow.emptyFlow()

    override suspend fun startScanning() {}
    override suspend fun stopScanning() {}
    override suspend fun connect(device: ScannedDevice) {}
    override suspend fun disconnect() {}
    override suspend fun setColorScheme(schemeIndex: Int) {}
    override suspend fun sendWorkoutCommand(command: ByteArray) {}
    override fun enableHandleDetection(enabled: Boolean) {}
}
