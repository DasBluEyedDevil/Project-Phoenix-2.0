package com.example.vitruvianredux.di

import com.example.vitruvianredux.data.local.DriverFactory
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.prefs.Preferences

import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.data.repository.StubBleRepository
import com.example.vitruvianredux.util.CsvExporter
import com.example.vitruvianredux.util.DesktopCsvExporter

actual val platformModule: Module = module {
    single { DriverFactory() }
    single<BleRepository> { StubBleRepository() }
    single<Settings> {
        val preferences = Preferences.userRoot().node("vitruvian_preferences")
        PreferencesSettings(preferences)
    }
    single<CsvExporter> { DesktopCsvExporter() }
}
