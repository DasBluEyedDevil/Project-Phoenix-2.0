package com.example.vitruvianredux.data.local

import com.example.vitruvianredux.database.VitruvianDatabase

class DatabaseFactory(private val driverFactory: DriverFactory) {
    fun createDatabase(): VitruvianDatabase {
        return VitruvianDatabase(driverFactory.createDriver())
    }
}
