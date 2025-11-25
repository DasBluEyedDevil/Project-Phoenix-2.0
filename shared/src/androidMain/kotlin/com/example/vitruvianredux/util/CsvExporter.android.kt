package com.example.vitruvianredux.util

import com.example.vitruvianredux.domain.model.PersonalRecord
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.WorkoutSession

actual class CsvExporter {
    actual fun exportPersonalRecords(
        personalRecords: List<PersonalRecord>,
        exerciseNames: Map<String, String>,
        weightUnit: WeightUnit,
        formatWeight: (Float, WeightUnit) -> String
    ): Result<String> {
        return Result.failure(NotImplementedError("Android CsvExporter not implemented"))
    }

    actual fun exportWorkoutHistory(
        workoutSessions: List<WorkoutSession>,
        exerciseNames: Map<String, String>,
        weightUnit: WeightUnit,
        formatWeight: (Float, WeightUnit) -> String
    ): Result<String> {
        return Result.failure(NotImplementedError("Android CsvExporter not implemented"))
    }

    actual fun exportPRProgression(
        personalRecords: List<PersonalRecord>,
        exerciseNames: Map<String, String>,
        weightUnit: WeightUnit,
        formatWeight: (Float, WeightUnit) -> String
    ): Result<String> {
        return Result.failure(NotImplementedError("Android CsvExporter not implemented"))
    }

    actual fun shareCSV(fileUri: String, fileName: String) {
        // TODO: Implement sharing
    }
}
