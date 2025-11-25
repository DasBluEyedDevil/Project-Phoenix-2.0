package com.example.vitruvianredux.di

import com.example.vitruvianredux.data.local.DatabaseFactory
import com.example.vitruvianredux.data.preferences.PreferencesManager
import com.example.vitruvianredux.data.preferences.SettingsPreferencesManager
import com.example.vitruvianredux.data.repository.*
import com.example.vitruvianredux.domain.usecase.RepCounterFromMachine
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.presentation.viewmodel.ConnectionLogsViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformModule: Module

val commonModule = module {
    // Database
    // DriverFactory is provided by platformModule
    single { DatabaseFactory(get()).createDatabase() }
    
    // Repositories
    // BleRepository is provided by platformModule
    single<WorkoutRepository> { SqlDelightWorkoutRepository(get()) }
    single<ExerciseRepository> { SqlDelightExerciseRepository(get()) }
    single<PersonalRecordRepository> { SqlDelightPersonalRecordRepository(get()) }
    
    // Preferences
    // Settings is provided by platformModule
    single<PreferencesManager> { SettingsPreferencesManager(get()) }
    
    // Use Cases
    single { RepCounterFromMachine() }
    
    // ViewModels
    factory { MainViewModel(get(), get(), get(), get(), get(), get()) }
    factory { ConnectionLogsViewModel() }
}