package com.example.vitruvianredux.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents the different workout modes available on Vitruvian machines.
 */
@Serializable
enum class WorkoutMode(val displayName: String, val description: String) {
    OLD_SCHOOL(
        displayName = "Old School",
        description = "Traditional weight training - constant resistance throughout the movement"
    ),
    PUMP(
        displayName = "Pump",
        description = "Higher reps with moderate weight for muscle pump and endurance"
    ),
    TUT(
        displayName = "Time Under Tension",
        description = "Slower eccentric phase for increased time under tension"
    ),
    TUT_BEAST(
        displayName = "TUT Beast",
        description = "Extended time under tension with heavier loads"
    ),
    ECCENTRIC(
        displayName = "Eccentric Only",
        description = "Focus on the negative/lowering phase with increased resistance"
    ),
    ECHO(
        displayName = "Echo",
        description = "Adaptive resistance that matches your movement pattern"
    )
}

/**
 * Represents the current state of the Vitruvian machine connection.
 */
@Serializable
enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

/**
 * Real-time metrics from the Vitruvian machine during a workout.
 */
@Serializable
data class WorkoutMetrics(
    val position: Float = 0f,
    val velocity: Float = 0f,
    val load: Float = 0f,
    val power: Float = 0f,
    val timestamp: Long = 0L
)

/**
 * Configuration for a workout set.
 */
@Serializable
data class WorkoutConfiguration(
    val exerciseId: Int,
    val exerciseName: String,
    val mode: WorkoutMode,
    val targetWeight: Float,
    val targetReps: Int,
    val useAmrap: Boolean = false
)

/**
 * Represents a completed workout session.
 */
@Serializable
data class WorkoutSession(
    val id: Long = 0,
    val exerciseId: Int,
    val exerciseName: String,
    val startTime: Long,
    val endTime: Long? = null,
    val mode: WorkoutMode,
    val targetWeight: Float,
    val targetReps: Int,
    val completedReps: Int = 0,
    val totalVolume: Float = 0f,
    val metrics: List<WorkoutMetrics> = emptyList()
)

/**
 * Represents a personal record for an exercise.
 */
@Serializable
data class PersonalRecord(
    val id: Long = 0,
    val exerciseId: Int,
    val exerciseName: String,
    val weight: Float,
    val reps: Int,
    val oneRepMax: Float,
    val achievedAt: Long
)
