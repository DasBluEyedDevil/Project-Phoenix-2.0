package com.example.vitruvianredux.di

import android.content.Context
import com.example.vitruvianredux.data.local.DriverFactory
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext

import com.example.vitruvianredux.data.repository.AndroidBleRepository
import com.example.vitruvianredux.data.repository.BleRepository

actual val platformModule: Module = module {
    single { DriverFactory(androidContext()) }
    single<BleRepository> { AndroidBleRepository(androidContext()) }
    single<Settings> {
        val preferences = androidContext().getSharedPreferences("vitruvian_preferences", Context.MODE_PRIVATE)
        SharedPreferencesSettings(preferences)
    }
}
