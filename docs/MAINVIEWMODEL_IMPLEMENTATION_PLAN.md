# MainViewModel Implementation Plan

## Overview

This document outlines the detailed implementation plan to achieve parity between the KMP `MainViewModel.kt` (783 lines, ~29KB) and the parent Android project's `MainViewModel.kt` (2,479 lines, ~114KB).

**Current Gap:** ~1,700 lines of complex state management logic

---

## Phase 1: Core Infrastructure (Foundation)

### 1.1 Thread-Safe State Management

**Location:** `shared/src/commonMain/.../presentation/viewmodel/MainViewModel.kt`

Add atomic state tracking for auto-stop logic:

```kotlin
// Add to imports
import kotlinx.atomicfu.atomic

// Add member variables
private val autoStopTriggered = atomic(false)
private val autoStopStopRequested = atomic(false)
```

**Files to modify:**
- `MainViewModel.kt` - Add atomic state variables

**Estimated effort:** 15 minutes

---

### 1.2 Platform Time Utilities (expect/actual)

The parent uses `java.time.LocalDate` for streak calculation. Need KMP equivalent.

**Location:** `shared/src/commonMain/.../util/TimeUtils.kt` (new file)

```kotlin
// commonMain
expect fun currentTimeMillis(): Long
expect fun getLocalDate(timestampMillis: Long): LocalDateInfo

data class LocalDateInfo(
    val year: Int,
    val month: Int,
    val dayOfMonth: Int
) {
    fun minusDays(days: Long): LocalDateInfo
    fun isBefore(other: LocalDateInfo): Boolean
}
```

**Platform implementations:**
- `androidMain/TimeUtils.android.kt` - Use `java.time.LocalDate`
- `desktopMain/TimeUtils.desktop.kt` - Use `java.time.LocalDate`
- `iosMain/TimeUtils.ios.kt` - Use `NSDate` / `NSCalendar`

**Estimated effort:** 30 minutes

---

## Phase 2: Handle State & Auto-Start (Critical)

### 2.1 Handle State Flow in BleRepository

**Location:** `shared/src/commonMain/.../data/repository/BleRepository.kt`

Add handle state tracking:

```kotlin
interface BleRepository {
    // ... existing ...

    // Handle state for auto-start/auto-stop
    val handleState: StateFlow<HandleState>

    // Enable handle detection
    suspend fun enableHandleDetection()

    // Enable Just Lift waiting mode (velocity-based wake-up)
    suspend fun enableJustLiftWaitingMode()
}

enum class HandleState {
    WaitingForRest,  // Handles at rest position
    Grabbed,          // User grabbed handles (3kg+ force)
    Released,         // User released handles
    Moving            // Handles in motion
}
```

**Files to modify:**
- `BleRepository.kt` - Add interface methods
- `AndroidBleRepository.kt` - Implement for Android
- `VitruvianBleManager.kt` - Add handle detection logic

**Estimated effort:** 2 hours

---

### 2.2 Auto-Start Timer Implementation

**Location:** `MainViewModel.kt`

```kotlin
private var autoStartJob: Job? = null

private fun startAutoStartTimer() {
    if (autoStartJob != null || workoutState.value !is WorkoutState.Idle) {
        return
    }

    autoStartJob = viewModelScope.launch {
        // 5 second countdown with visible progress
        for (i in 5 downTo 1) {
            _autoStartCountdown.value = i
            delay(1000)
        }
        _autoStartCountdown.value = null
        startWorkout(isJustLiftMode = true)
    }
}

private fun cancelAutoStartTimer() {
    autoStartJob?.cancel()
    autoStartJob = null
    _autoStartCountdown.value = null
}
```

**Add handle state collector in init block:**

```kotlin
// In init {}
viewModelScope.launch {
    bleRepository.handleState.collect { state ->
        // Track current handle state
        currentHandleState = state

        val isIdle = workoutState.value is WorkoutState.Idle
        val isSummaryAndJustLift = workoutState.value is WorkoutState.SetSummary &&
                                   workoutParameters.value.isJustLift

        if (workoutParameters.value.useAutoStart && (isIdle || isSummaryAndJustLift)) {
            when (state) {
                HandleState.Grabbed -> startAutoStartTimer()
                HandleState.Released -> cancelAutoStartTimer()
                else -> {}
            }
        }

        // Auto-STOP logic for Just Lift mode
        if (workoutParameters.value.isJustLift && workoutState.value is WorkoutState.Active) {
            if (state == HandleState.Released) {
                if (autoStopStartTime == null) {
                    autoStopStartTime = currentTimeMillis()
                }
            } else {
                resetAutoStopTimer()
            }
        }
    }
}
```

**Estimated effort:** 1 hour

---

## Phase 3: Auto-Stop Logic (Critical for Just Lift/AMRAP)

### 3.1 Danger Zone Detection

**Location:** `MainViewModel.kt`

Add constants and detection logic:

```kotlin
companion object {
    const val AUTO_STOP_DURATION_SECONDS = 3f
    const val HANDLE_REST_THRESHOLD = 2.5
    const val MIN_RANGE_THRESHOLD = 50
    const val TEMP_SINGLE_EXERCISE_PREFIX = "temp_single_"
}

private var autoStopStartTime: Long? = null

private fun checkAutoStop(metric: WorkoutMetric) {
    val hasMeaningful = repCounter.hasMeaningfulRange()
    val params = workoutParameters.value

    if (!hasMeaningful) {
        resetAutoStopTimer()
        return
    }

    val inDangerZone = repCounter.isInDangerZone(metric.positionA, metric.positionB)
    val repRanges = repCounter.getRepRanges()

    // Check cable A release
    var cableInDangerAndReleased = false
    if (repRanges.minPosA != null && repRanges.maxPosA != null) {
        val rangeA = repRanges.maxPosA!! - repRanges.minPosA!!
        if (rangeA > MIN_RANGE_THRESHOLD) {
            val thresholdA = repRanges.minPosA!! + (rangeA * 0.05f).toInt()
            val cableAInDanger = metric.positionA <= thresholdA
            val cableAReleased = metric.positionA.toDouble() < HANDLE_REST_THRESHOLD ||
                                (metric.positionA - repRanges.minPosA!!) < 10
            if (cableAInDanger && cableAReleased) {
                cableInDangerAndReleased = true
            }
        }
    }

    // Similar check for cable B...

    if (inDangerZone && cableInDangerAndReleased) {
        val startTime = autoStopStartTime ?: run {
            autoStopStartTime = currentTimeMillis()
            currentTimeMillis()
        }

        val elapsed = (currentTimeMillis() - startTime) / 1000f
        val progress = (elapsed / AUTO_STOP_DURATION_SECONDS).coerceIn(0f, 1f)
        val remaining = (AUTO_STOP_DURATION_SECONDS - elapsed).coerceAtLeast(0f)

        _autoStopState.value = AutoStopUiState(
            isActive = true,
            progress = progress,
            secondsRemaining = ceil(remaining).toInt()
        )

        if (elapsed >= AUTO_STOP_DURATION_SECONDS) {
            triggerAutoStop()
        }
    } else {
        resetAutoStopTimer()
    }
}
```

**Estimated effort:** 1.5 hours

---

### 3.2 Auto-Stop Trigger and Reset

```kotlin
private fun requestAutoStop() {
    if (autoStopStopRequested.getAndSet(true)) return
    triggerAutoStop()
}

private fun triggerAutoStop() {
    autoStopTriggered.value = true
    if (workoutParameters.value.isJustLift || workoutParameters.value.isAMRAP) {
        _autoStopState.value = _autoStopState.value.copy(
            progress = 1f,
            secondsRemaining = 0,
            isActive = true
        )
    } else {
        _autoStopState.value = AutoStopUiState()
    }
    handleSetCompletion()
}

private fun resetAutoStopTimer() {
    autoStopStartTime = null
    if (!autoStopTriggered.value) {
        _autoStopState.value = AutoStopUiState()
    }
}

private fun resetAutoStopState() {
    autoStopStartTime = null
    autoStopTriggered.value = false
    autoStopStopRequested.value = false
    _autoStopState.value = AutoStopUiState()
}
```

**Estimated effort:** 30 minutes

---

## Phase 4: Rep Notification Handling (Critical)

### 4.1 Rep Events Flow in BleRepository

**Location:** `shared/src/commonMain/.../data/repository/BleRepository.kt`

```kotlin
interface BleRepository {
    // ... existing ...

    // Rep notifications from machine
    val repEvents: Flow<RepNotification>
}

data class RepNotification(
    val topCounter: Int,      // Rep started (at top position)
    val completeCounter: Int, // Rep completed (at bottom position)
    val repsRomCount: Int,    // Machine's warmup rep count
    val repsSetCount: Int     // Machine's working rep count
)
```

### 4.2 Handle Rep Notifications in ViewModel

```kotlin
// In init {}
viewModelScope.launch {
    bleRepository.repEvents.collect { notification ->
        val state = workoutState.value
        if (state is WorkoutState.Active) {
            handleRepNotification(notification)
        }
    }
}

private fun handleRepNotification(notification: RepNotification) {
    val currentPositions = currentMetric.value

    // Use machine's ROM and Set counters directly (official app method)
    repCounter.process(
        repsRomCount = notification.repsRomCount,
        repsSetCount = notification.repsSetCount,
        up = notification.topCounter,
        down = notification.completeCounter,
        posA = currentPositions?.positionA ?: 0,
        posB = currentPositions?.positionB ?: 0
    )

    // Update rep ranges for position bars visualization
    _repRanges.value = repCounter.getRepRanges()
}
```

**Estimated effort:** 1 hour

---

## Phase 5: Rest Timer & Set Progression (Critical)

### 5.1 Rest Timer Implementation

```kotlin
private var restTimerJob: Job? = null

private fun startRestTimer() {
    restTimerJob?.cancel()

    restTimerJob = viewModelScope.launch {
        val routine = loadedRoutine.value
        val currentExercise = routine?.exercises?.getOrNull(currentExerciseIndex.value)

        val completedSetIndex = currentSetIndex.value
        val restDuration = currentExercise?.getRestForSet(completedSetIndex)?.takeIf { it > 0 } ?: 90
        val autoplay = userPreferences.value.autoplayEnabled

        val isSingleExercise = isSingleExerciseMode()

        for (i in restDuration downTo 1) {
            val nextName = calculateNextExerciseName(isSingleExercise, currentExercise, routine)

            _workoutState.value = WorkoutState.Resting(
                restSecondsRemaining = i,
                nextExerciseName = nextName,
                isLastExercise = calculateIsLastExercise(isSingleExercise, currentExercise, routine),
                currentSet = currentSetIndex.value + 1,
                totalSets = currentExercise?.setReps?.size ?: 0
            )
            delay(1000)
        }

        if (autoplay) {
            if (isSingleExercise) {
                advanceToNextSetInSingleExercise()
            } else {
                startNextSetOrExercise()
            }
        } else {
            // Stay in resting state with 0 seconds - user must manually start
            _workoutState.value = WorkoutState.Resting(
                restSecondsRemaining = 0,
                nextExerciseName = calculateNextExerciseName(isSingleExercise, currentExercise, routine),
                isLastExercise = calculateIsLastExercise(isSingleExercise, currentExercise, routine),
                currentSet = currentSetIndex.value + 1,
                totalSets = currentExercise?.setReps?.size ?: 0
            )
        }
    }
}
```

**Estimated effort:** 1.5 hours

---

### 5.2 Set/Exercise Progression

```kotlin
private fun startNextSetOrExercise() {
    val currentState = workoutState.value
    if (currentState is WorkoutState.Completed) return
    if (currentState !is WorkoutState.Resting) return

    val routine = loadedRoutine.value ?: return
    val currentExercise = routine.exercises.getOrNull(currentExerciseIndex.value) ?: return

    if (currentSetIndex.value < currentExercise.setReps.size - 1) {
        // More sets in current exercise
        _currentSetIndex.value++
        val targetReps = currentExercise.setReps[currentSetIndex.value]
        val setWeight = currentExercise.setWeightsPerCableKg.getOrNull(currentSetIndex.value)
            ?: currentExercise.weightPerCableKg

        _workoutParameters.value = workoutParameters.value.copy(
            reps = targetReps ?: 0,
            weightPerCableKg = setWeight,
            isAMRAP = targetReps == null
        )
        startWorkout(skipCountdown = true)
    } else {
        // Move to next exercise
        if (currentExerciseIndex.value < routine.exercises.size - 1) {
            _currentExerciseIndex.value++
            _currentSetIndex.value = 0

            val nextExercise = routine.exercises[currentExerciseIndex.value]
            val nextSetReps = nextExercise.setReps.getOrNull(0)
            val nextSetWeight = nextExercise.setWeightsPerCableKg.getOrNull(0)
                ?: nextExercise.weightPerCableKg

            _workoutParameters.value = workoutParameters.value.copy(
                weightPerCableKg = nextSetWeight,
                reps = nextSetReps ?: 0,
                workoutType = nextExercise.workoutType,
                progressionRegressionKg = nextExercise.progressionKg,
                selectedExerciseId = nextExercise.exercise.id,
                isAMRAP = nextSetReps == null
            )
            startWorkout(skipCountdown = true)
        } else {
            // Routine complete
            _workoutState.value = WorkoutState.Completed
            _loadedRoutine.value = null
            _currentSetIndex.value = 0
            _currentExerciseIndex.value = 0
            currentRoutineSessionId = null
            currentRoutineName = null
            repCounter.reset()
            resetAutoStopState()
        }
    }
}

fun skipRest() {
    if (workoutState.value is WorkoutState.Resting) {
        restTimerJob?.cancel()
        restTimerJob = null

        val isSingleExercise = isSingleExerciseMode() && !workoutParameters.value.isJustLift
        if (isSingleExercise) {
            advanceToNextSetInSingleExercise()
        } else {
            startNextSetOrExercise()
        }
    }
}
```

**Estimated effort:** 2 hours

---

## Phase 6: Set Completion & PR Detection

### 6.1 Handle Set Completion

```kotlin
private fun handleSetCompletion() {
    viewModelScope.launch {
        val params = workoutParameters.value
        val isJustLift = params.isJustLift

        // Stop hardware
        bleRepository.sendWorkoutCommand(BlePacketFactory.createStopCommand())

        // Just Lift: Immediately restart polling to clear machine fault state
        if (isJustLift) {
            bleRepository.restartMonitorPolling()
        }

        _hapticEvents.emit(HapticEvent.WORKOUT_END)

        // Save progress
        saveWorkoutSession()

        // Calculate metrics for summary
        val peakPerCableKg = if (collectedMetrics.isNotEmpty()) {
            collectedMetrics.maxOf { it.totalLoad } / 2f
        } else {
            params.weightPerCableKg
        }

        val averagePerCableKg = if (collectedMetrics.isNotEmpty()) {
            collectedMetrics.map { it.totalLoad / 2f }.average().toFloat()
        } else {
            params.weightPerCableKg
        }

        val completedReps = repCount.value.workingReps

        // Show set summary
        _workoutState.value = WorkoutState.SetSummary(
            metrics = collectedMetrics.toList(),
            peakPower = peakPerCableKg,
            averagePower = averagePerCableKg,
            repCount = completedReps
        )

        // Just Lift: Auto-advance to next set after showing summary
        if (isJustLift) {
            repCounter.reset()
            resetAutoStopState()
            enableHandleDetection()
            bleRepository.enableJustLiftWaitingMode()

            delay(5000) // Show summary for 5 seconds

            if (workoutState.value is WorkoutState.SetSummary) {
                resetForNewWorkout()
                _workoutState.value = WorkoutState.Idle
            }
        }
    }
}
```

**Estimated effort:** 1.5 hours

---

### 6.2 PR Detection

```kotlin
private suspend fun saveWorkoutSession() {
    val sessionId = currentSessionId ?: return
    val params = workoutParameters.value
    val warmup = repCount.value.warmupReps
    val working = repCount.value.workingReps
    val duration = currentTimeMillis() - workoutStartTime

    val measuredPerCableKg = if (collectedMetrics.isNotEmpty()) {
        collectedMetrics.maxOf { it.totalLoad } / 2f
    } else {
        params.weightPerCableKg
    }

    // ... create and save session ...

    // Track personal record if exercise is selected
    params.selectedExerciseId?.let { exerciseId ->
        val isEchoMode = params.workoutType is WorkoutType.Echo
        if (working > 0 && !params.isJustLift && !isEchoMode) {
            val isNewPR = workoutRepository.updatePersonalRecordIfNeeded(
                exerciseId = exerciseId,
                weightPerCableKg = measuredPerCableKg,
                reps = working,
                workoutMode = params.workoutType.displayName
            )
            if (isNewPR) {
                // Trigger PR celebration
                val exercise = exerciseRepository.getExerciseById(exerciseId)
                _prCelebrationEvent.emit(
                    PRCelebrationEvent(
                        exerciseName = exercise?.name ?: "Unknown Exercise",
                        weightPerCableKg = measuredPerCableKg,
                        reps = working,
                        workoutMode = params.workoutType.displayName
                    )
                )
            }
        }
    }
}
```

**Estimated effort:** 1 hour

---

## Phase 7: Workout Streak Calculation

### 7.1 Implement Streak Logic

Replace the stub in KMP:

```kotlin
// Current (stub):
val workoutStreak: StateFlow<Int?> = MutableStateFlow(null)

// Replace with:
val workoutStreak: StateFlow<Int?> = allWorkoutSessions.map { sessions ->
    if (sessions.isEmpty()) return@map null

    val workoutDates = sessions
        .map { getLocalDate(it.timestamp) }
        .distinctBy { "${it.year}-${it.month}-${it.dayOfMonth}" }
        .sortedByDescending { it.year * 10000 + it.month * 100 + it.dayOfMonth }

    val today = getLocalDate(currentTimeMillis())
    val lastWorkoutDate = workoutDates.first()

    // Check if streak is current (workout today or yesterday)
    if (lastWorkoutDate.isBefore(today.minusDays(1))) {
        return@map null // Streak broken
    }

    var streak = 1
    for (i in 1 until workoutDates.size) {
        if (workoutDates[i] == workoutDates[i-1].minusDays(1)) {
            streak++
        } else {
            break // Found a gap
        }
    }
    streak
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
```

**Estimated effort:** 30 minutes

---

## Phase 8: Bodyweight Exercise Support

### 8.1 Bodyweight Detection and Timer

```kotlin
private var bodyweightTimerJob: Job? = null

private fun isBodyweightExercise(exercise: RoutineExercise?): Boolean {
    return exercise?.let {
        val equipment = it.exercise.equipment
        equipment.isEmpty() || equipment.equals("bodyweight", ignoreCase = true)
    } ?: false
}

// In startWorkout():
val currentExercise = loadedRoutine.value?.exercises?.getOrNull(currentExerciseIndex.value)
val isBodyweight = isBodyweightExercise(currentExercise)
val isBodyweightDuration = isBodyweight && currentExercise?.duration != null

if (isBodyweightDuration) {
    val duration = currentExercise?.duration ?: 30

    _workoutState.value = WorkoutState.Active
    _hapticEvents.emit(HapticEvent.WORKOUT_START)

    bodyweightTimerJob?.cancel()
    bodyweightTimerJob = viewModelScope.launch {
        delay(duration * 1000L)
        handleSetCompletion()
    }
} else {
    // Normal cable-based exercise...
}
```

**Estimated effort:** 1 hour

---

## Phase 9: Platform-Specific Services (Android Only)

### 9.1 Foreground Service Integration

This needs to be platform-specific. Create expect/actual:

**Location:** `shared/src/commonMain/.../service/WorkoutServiceManager.kt`

```kotlin
expect class WorkoutServiceManager {
    fun startWorkoutService(exerciseName: String, targetReps: Int)
    fun stopWorkoutService()
}
```

**Android implementation:**

```kotlin
actual class WorkoutServiceManager(private val context: Context) {
    actual fun startWorkoutService(exerciseName: String, targetReps: Int) {
        WorkoutForegroundService.startWorkoutService(context, exerciseName, targetReps)
    }

    actual fun stopWorkoutService() {
        WorkoutForegroundService.stopWorkoutService(context)
    }
}
```

**Desktop/iOS implementation (no-op):**

```kotlin
actual class WorkoutServiceManager {
    actual fun startWorkoutService(exerciseName: String, targetReps: Int) {
        // No-op on desktop/iOS
    }

    actual fun stopWorkoutService() {
        // No-op on desktop/iOS
    }
}
```

**Estimated effort:** 2 hours

---

## Implementation Summary

| Phase | Description | Estimated Time |
|-------|-------------|----------------|
| 1 | Core Infrastructure | 45 min |
| 2 | Handle State & Auto-Start | 3 hours |
| 3 | Auto-Stop Logic | 2 hours |
| 4 | Rep Notification Handling | 1 hour |
| 5 | Rest Timer & Set Progression | 3.5 hours |
| 6 | Set Completion & PR Detection | 2.5 hours |
| 7 | Workout Streak | 30 min |
| 8 | Bodyweight Exercise Support | 1 hour |
| 9 | Foreground Service | 2 hours |
| **TOTAL** | | **~16 hours** |

---

## Dependencies Between Phases

```
Phase 1 (Infrastructure)
    ↓
Phase 2 (Handle State) ←→ Phase 3 (Auto-Stop)
    ↓                           ↓
Phase 4 (Rep Notifications)
    ↓
Phase 5 (Rest Timer) → Phase 6 (Set Completion)
                              ↓
                       Phase 7 (Streak) + Phase 8 (Bodyweight)
                              ↓
                       Phase 9 (Foreground Service)
```

---

## Testing Strategy

Each phase should be tested before moving to the next:

1. **Phase 1-2:** Test handle detection triggers auto-start countdown
2. **Phase 3:** Test Just Lift auto-stop when handles released
3. **Phase 4:** Test rep count matches machine display
4. **Phase 5:** Test rest timer counts down and advances sets
5. **Phase 6:** Test PR celebration fires on new records
6. **Phase 7:** Test streak calculation shows correct count
7. **Phase 8:** Test bodyweight exercise uses timer
8. **Phase 9:** Test Android notification appears during workout

---

## Files to Create/Modify

### New Files:
- `shared/src/commonMain/.../util/TimeUtils.kt`
- `shared/src/androidMain/.../util/TimeUtils.android.kt`
- `shared/src/desktopMain/.../util/TimeUtils.desktop.kt`
- `shared/src/iosMain/.../util/TimeUtils.ios.kt`
- `shared/src/commonMain/.../service/WorkoutServiceManager.kt`
- `shared/src/androidMain/.../service/WorkoutServiceManager.android.kt`
- `shared/src/desktopMain/.../service/WorkoutServiceManager.desktop.kt`
- `shared/src/iosMain/.../service/WorkoutServiceManager.ios.kt`

### Modified Files:
- `shared/src/commonMain/.../presentation/viewmodel/MainViewModel.kt` (major changes)
- `shared/src/commonMain/.../data/repository/BleRepository.kt` (add interfaces)
- `shared/src/androidMain/.../data/repository/AndroidBleRepository.kt` (implement new interfaces)
- `shared/src/commonMain/.../domain/usecase/RepCounterFromMachine.kt` (ensure methods exist)

---

*Document created: 2025-11-27*
*Based on comparison of parent MainViewModel.kt (2,479 lines) vs KMP MainViewModel.kt (783 lines)*
