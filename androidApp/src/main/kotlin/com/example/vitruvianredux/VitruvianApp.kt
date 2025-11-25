package com.example.vitruvianredux

import android.app.Application
import co.touchlab.kermit.Logger
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class VitruvianApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Koin for dependency injection
        startKoin {
            androidLogger()
            androidContext(this@VitruvianApp)
            modules(appModule)
        }
        
        Logger.d("VitruvianApp") { "Application initialized" }
    }
}
