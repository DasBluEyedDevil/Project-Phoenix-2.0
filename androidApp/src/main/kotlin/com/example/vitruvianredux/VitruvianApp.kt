package com.example.vitruvianredux

import android.app.Application
import co.touchlab.kermit.Logger
import com.example.vitruvianredux.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class VitruvianApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        initKoin {
            androidLogger()
            androidContext(this@VitruvianApp)
        }
        
        Logger.d("VitruvianApp") { "Application initialized" }
    }
}