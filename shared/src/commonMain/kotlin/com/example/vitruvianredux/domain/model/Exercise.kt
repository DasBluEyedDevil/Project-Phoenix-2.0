package com.example.vitruvianredux.domain.model

/**
 * Cable configuration for Vitruvian exercises
 * - SINGLE: One cable only (unilateral - e.g., one-arm row)
 * - DOUBLE: Both cables required (bilateral - e.g., bench press)
 * - EITHER: User can choose single or double (e.g., bicep curls)
 */
enum class CableConfiguration {
    SINGLE,
    DOUBLE,
    EITHER
}

/**
 * Exercise model - represents any exercise that can be performed on the Vitruvian Trainer
 *
 * MIGRATION NOTE: This was converted from an enum to a data class to support the exercise library
 * with 100+ exercises instead of being limited to hardcoded values.
 *
 * NOTES:
 * - Vitruvian cables only pull UPWARD from floor platform
 * - Compatible: Rows, presses, curls, squats, deadlifts, raises
 * - NOT compatible: Pulldowns, pushdowns (require overhead anchor)
 * - Machine tracks each cable independently (loadA, loadB, posA, posB)
 * - Weight is always specified as "per cable" in the BLE protocol
 */
data class Exercise(
    val name: String,
    val muscleGroup: String,
    val muscleGroups: String = muscleGroup, // Comma-separated list of primary muscle groups (defaults to muscleGroup for backward compatibility)
    val equipment: String = "",
    val defaultCableConfig: CableConfiguration = CableConfiguration.DOUBLE,
    val id: String? = null  // Optional exercise library ID for loading videos/thumbnails
) {
    /**
     * Display name for UI (same as name for now)
     */
    val displayName: String
        get() = name
}

/**
 * Exercise categories for organization
 * Used primarily for filtering and grouping in the UI
 */
enum class ExerciseCategory(val displayName: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    LEGS("Legs"),
    GLUTES("Glutes"),
    CORE("Core"),
    FULL_BODY("Full Body")
}
