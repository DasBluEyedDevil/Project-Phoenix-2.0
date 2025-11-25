package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vitruvianredux.data.repository.AutoStopUiState
import com.example.vitruvianredux.domain.model.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Workout Tab - displays workout controls during active workout
 * TODO: Full implementation pending
 */
@Composable
fun WorkoutTab(
    modifier: Modifier = Modifier,
    workoutState: WorkoutState,
    currentMetric: WorkoutMetric?,
    repCount: RepCount,
    workoutParameters: WorkoutParameters,
    autoStopState: AutoStopUiState,
    hapticEvents: SharedFlow<HapticEvent>,
    weightUnit: WeightUnit,
    stopAtTop: Boolean,
    enableVideoPlayback: Boolean,
    loadedRoutine: Routine?,
    currentExerciseIndex: Int,
    currentSetIndex: Int,
    formatWeight: (Float, WeightUnit) -> String,
    kgToDisplay: (Float, WeightUnit) -> Float,
    onPauseWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    onEndWorkout: () -> Unit,
    onStopWorkout: () -> Unit,
    onProceedFromSummary: () -> Unit,
    onResetForNewWorkout: () -> Unit,
    onAdvanceToNextExercise: () -> Unit,
    onExerciseWeightChange: (Float) -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Workout Tab",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "State: ${workoutState::class.simpleName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Reps: ${repCount.workingReps}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "TODO: Full workout UI implementation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
