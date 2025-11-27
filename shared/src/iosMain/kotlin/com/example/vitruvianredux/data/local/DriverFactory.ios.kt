package com.example.vitruvianredux.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.example.vitruvianredux.database.VitruvianDatabase

actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(VitruvianDatabase.Schema, "vitruvian.db")
    }
}
