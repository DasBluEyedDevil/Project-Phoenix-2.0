package com.example.vitruvianredux.data.preferences

import com.example.vitruvianredux.domain.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * User preferences data class
 */
data class UserPreferences(
    val weightUnit: WeightUnit = WeightUnit.LB,
    val autoplayEnabled: Boolean = true,
    val stopAtTop: Boolean = false,
    val enableVideoPlayback: Boolean = true,
    val colorScheme: Int = 0
)

/**
 * Single exercise defaults for saving/loading exercise configurations
 */
@Serializable
data class SingleExerciseDefaults(
    val exerciseId: String,
    val cableConfig: String,
    val setReps: List<Int?>,
    val weightPerCableKg: Float,
    val setWeightsPerCableKg: List<Float>,
    val progressionKg: Float,
    val setRestSeconds: List<Int>,
    val workoutModeId: Int,
    val eccentricLoadPercentage: Int,
    val echoLevelValue: Int,
    val duration: Int,
    val isAMRAP: Boolean,
    val perSetRestTime: Boolean
) {
    fun getCableConfiguration(): com.example.vitruvianredux.domain.model.CableConfiguration {
        return com.example.vitruvianredux.domain.model.CableConfiguration.entries.find { it.name == cableConfig }
            ?: com.example.vitruvianredux.domain.model.CableConfiguration.DOUBLE
    }

    fun getEccentricLoad(): com.example.vitruvianredux.domain.model.EccentricLoad {
        return com.example.vitruvianredux.domain.model.EccentricLoad.entries.find { it.percentage == eccentricLoadPercentage }
            ?: com.example.vitruvianredux.domain.model.EccentricLoad.LOAD_100
    }

    fun getEchoLevel(): com.example.vitruvianredux.domain.model.EchoLevel {
        return com.example.vitruvianredux.domain.model.EchoLevel.entries.find { it.levelValue == echoLevelValue }
            ?: com.example.vitruvianredux.domain.model.EchoLevel.HARDER
    }

    fun toWorkoutType(): com.example.vitruvianredux.domain.model.WorkoutType {
        return when (workoutModeId) {
            0 -> com.example.vitruvianredux.domain.model.WorkoutType.Program(com.example.vitruvianredux.domain.model.ProgramMode.OldSchool)
            2 -> com.example.vitruvianredux.domain.model.WorkoutType.Program(com.example.vitruvianredux.domain.model.ProgramMode.Pump)
            3 -> com.example.vitruvianredux.domain.model.WorkoutType.Program(com.example.vitruvianredux.domain.model.ProgramMode.TUT)
            4 -> com.example.vitruvianredux.domain.model.WorkoutType.Program(com.example.vitruvianredux.domain.model.ProgramMode.TUTBeast)
            6 -> com.example.vitruvianredux.domain.model.WorkoutType.Program(com.example.vitruvianredux.domain.model.ProgramMode.EccentricOnly)
            10 -> com.example.vitruvianredux.domain.model.WorkoutType.Echo(getEchoLevel(), getEccentricLoad())
            else -> com.example.vitruvianredux.domain.model.WorkoutType.Program(com.example.vitruvianredux.domain.model.ProgramMode.OldSchool)
        }
    }
}

/**
 * Just Lift defaults
 */
@Serializable
data class JustLiftDefaults(
    val workoutModeId: Int = 0,
    val weightPerCableKg: Float = 20f,
    val weightChangePerRep: Float = 0f,
    val eccentricLoadPercentage: Int = 100,
    val echoLevelValue: Int = 2
)

/**
 * Preferences Manager interface
 * TODO: Implement with multiplatform-settings for actual persistence
 */
interface PreferencesManager {
    val preferencesFlow: StateFlow<UserPreferences>

    suspend fun setWeightUnit(unit: WeightUnit)
    suspend fun setAutoplayEnabled(enabled: Boolean)
    suspend fun setStopAtTop(enabled: Boolean)
    suspend fun setEnableVideoPlayback(enabled: Boolean)
    suspend fun setColorScheme(scheme: Int)

    suspend fun getSingleExerciseDefaults(exerciseId: String, cableConfig: String): SingleExerciseDefaults?
    suspend fun saveSingleExerciseDefaults(defaults: SingleExerciseDefaults)

    suspend fun getJustLiftDefaults(): JustLiftDefaults
    suspend fun saveJustLiftDefaults(defaults: JustLiftDefaults)
}

/**
 * Stub Preferences Manager for compilation
 */
class StubPreferencesManager : PreferencesManager {
    private val _preferencesFlow = MutableStateFlow(UserPreferences())
    override val preferencesFlow: StateFlow<UserPreferences> = _preferencesFlow

    override suspend fun setWeightUnit(unit: WeightUnit) {
        _preferencesFlow.value = _preferencesFlow.value.copy(weightUnit = unit)
    }

    override suspend fun setAutoplayEnabled(enabled: Boolean) {
        _preferencesFlow.value = _preferencesFlow.value.copy(autoplayEnabled = enabled)
    }

    override suspend fun setStopAtTop(enabled: Boolean) {
        _preferencesFlow.value = _preferencesFlow.value.copy(stopAtTop = enabled)
    }

    override suspend fun setEnableVideoPlayback(enabled: Boolean) {
        _preferencesFlow.value = _preferencesFlow.value.copy(enableVideoPlayback = enabled)
    }

    override suspend fun setColorScheme(scheme: Int) {
        _preferencesFlow.value = _preferencesFlow.value.copy(colorScheme = scheme)
    }

    override suspend fun getSingleExerciseDefaults(exerciseId: String, cableConfig: String): SingleExerciseDefaults? = null
    override suspend fun saveSingleExerciseDefaults(defaults: SingleExerciseDefaults) {}

    override suspend fun getJustLiftDefaults() = JustLiftDefaults()
    override suspend fun saveJustLiftDefaults(defaults: JustLiftDefaults) {}
}
