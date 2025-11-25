package com.example.vitruvianredux.data.preferences

import com.example.vitruvianredux.domain.model.WeightUnit
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsPreferencesManager(
    private val settings: Settings
) : PreferencesManager {

    private val _preferencesFlow = MutableStateFlow(loadPreferences())
    override val preferencesFlow: StateFlow<UserPreferences> = _preferencesFlow.asStateFlow()

    private fun loadPreferences(): UserPreferences {
        return UserPreferences(
            weightUnit = if (settings.getBoolean("is_kg", true)) WeightUnit.KG else WeightUnit.LB,
            autoplayEnabled = settings.getBoolean("autoplay_enabled", true),
            stopAtTop = settings.getBoolean("stop_at_top", false),
            enableVideoPlayback = settings.getBoolean("enable_video_playback", true),
            colorScheme = settings.getInt("color_scheme", 0)
        )
    }

    private fun updateState() {
        _preferencesFlow.value = loadPreferences()
    }

    override suspend fun setWeightUnit(unit: WeightUnit) {
        settings["is_kg"] = (unit == WeightUnit.KG)
        updateState()
    }

    override suspend fun setAutoplayEnabled(enabled: Boolean) {
        settings["autoplay_enabled"] = enabled
        updateState()
    }

    override suspend fun setStopAtTop(enabled: Boolean) {
        settings["stop_at_top"] = enabled
        updateState()
    }

    override suspend fun setEnableVideoPlayback(enabled: Boolean) {
        settings["enable_video_playback"] = enabled
        updateState()
    }

    override suspend fun setColorScheme(scheme: Int) {
        settings["color_scheme"] = scheme
        updateState()
    }

    override suspend fun getSingleExerciseDefaults(exerciseId: String, cableConfig: String): SingleExerciseDefaults? {
        // TODO: Serialize/Deserialize JSON string for complex objects
        return null
    }

    override suspend fun saveSingleExerciseDefaults(defaults: SingleExerciseDefaults) {
        // TODO
    }

    override suspend fun getJustLiftDefaults(): JustLiftDefaults {
        // TODO
        return JustLiftDefaults()
    }

    override suspend fun saveJustLiftDefaults(defaults: JustLiftDefaults) {
        // TODO
    }
}
