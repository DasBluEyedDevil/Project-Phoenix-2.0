package com.example.vitruvianredux.di

import com.example.vitruvianredux.data.local.DriverFactory
import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.data.repository.IosBleRepository
import com.example.vitruvianredux.util.CsvExporter
import com.example.vitruvianredux.util.IosCsvExporter
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual val platformModule: Module = module {
    single { DriverFactory() }
    single<BleRepository> { IosBleRepository() }
    single<Settings> {
        val defaults = NSUserDefaults.standardUserDefaults
        NSUserDefaultsSettings(defaults)
    }
    single<CsvExporter> { IosCsvExporter() }
}
