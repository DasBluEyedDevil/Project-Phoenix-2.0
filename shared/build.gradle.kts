plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.sqldelight)
}

kotlin {
    // Android target
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    // iOS targets
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }
    
    // Desktop targets
    jvm("desktop") {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                // Compose Multiplatform
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                
                // Kotlinx
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                
                // DI - Koin
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                
                // Database - SQLDelight
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
                
                // Settings/Preferences
                implementation(libs.multiplatform.settings)
                implementation(libs.multiplatform.settings.coroutines)
                
                // Logging
                implementation(libs.kermit)
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        
        val androidMain by getting {
            dependencies {
                // Android-specific Coroutines
                implementation(libs.kotlinx.coroutines.android)
                
                // SQLDelight Android Driver
                implementation(libs.sqldelight.android.driver)
                
                // Koin Android
                implementation(libs.koin.android)
                
                // BLE - Nordic Library (Android only for now)
                implementation(libs.nordic.ble)
                implementation(libs.nordic.ble.ktx)
            }
        }
        
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            
            dependencies {
                // SQLDelight Native Driver
                implementation(libs.sqldelight.native.driver)
            }
        }
        
        val desktopMain by getting {
            dependencies {
                // Compose Desktop
                implementation(compose.desktop.currentOs)
                
                // Coroutines Swing for desktop
                implementation(libs.kotlinx.coroutines.swing)
                
                // SQLDelight JVM Driver
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
    }
}

android {
    namespace = "com.example.vitruvianredux.shared"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 26
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("VitruvianDatabase") {
            packageName.set("com.example.vitruvianredux.database")
        }
    }
}
