package com.example.vitruvianredux.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a muscle group for categorizing exercises.
 */
@Serializable
enum class MuscleGroup(val displayName: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARMS("Forearms"),
    CORE("Core"),
    QUADS("Quadriceps"),
    HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"),
    CALVES("Calves"),
    FULL_BODY("Full Body")
}

/**
 * Represents the type of equipment used for an exercise.
 */
@Serializable
enum class EquipmentType(val displayName: String) {
    BARBELL("Barbell"),
    DUMBBELL("Dumbbell"),
    CABLE("Cable"),
    BODYWEIGHT("Bodyweight")
}

/**
 * Represents an exercise in the library.
 */
@Serializable
data class Exercise(
    val id: Int,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val equipment: EquipmentType = EquipmentType.CABLE,
    val description: String = "",
    val instructions: List<String> = emptyList(),
    val videoUrl: String? = null,
    val thumbnailUrl: String? = null,
    val defaultWeight: Float = 20f,
    val defaultReps: Int = 10,
    val defaultMode: WorkoutMode = WorkoutMode.OLD_SCHOOL
)

/**
 * Represents a custom workout routine.
 */
@Serializable
data class Routine(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val exercises: List<RoutineExercise> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Represents an exercise within a routine with specific configuration.
 */
@Serializable
data class RoutineExercise(
    val id: Long = 0,
    val routineId: Long,
    val exercise: Exercise,
    val orderIndex: Int,
    val targetWeight: Float,
    val targetReps: Int,
    val targetSets: Int,
    val mode: WorkoutMode
)
