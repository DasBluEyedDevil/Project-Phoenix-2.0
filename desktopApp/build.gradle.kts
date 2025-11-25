import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm("desktop") {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    sourceSets {
        val desktopMain by getting {
            dependencies {
                // Shared module
                implementation(project(":shared"))
                
                // Compose Desktop
                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                
                // Coroutines
                implementation(libs.kotlinx.coroutines.swing)
                
                // Koin DI
                implementation(libs.koin.core)
                
                // Logging
                implementation(libs.kermit)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.vitruvianredux.MainKt"
        
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            
            packageName = "VitruvianProjectPhoenix"
            packageVersion = "0.1.0"
            description = "Vitruvian Project Phoenix - Control app for Vitruvian Trainer machines"
            copyright = "© 2024 Vitruvian Project Phoenix Contributors"
            vendor = "Vitruvian Project Phoenix"
            
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
            
            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
                menuGroup = "Vitruvian Project Phoenix"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }
            
            macOS {
                iconFile.set(project.file("src/main/resources/icon.icns"))
                bundleID = "com.example.vitruvianredux"
            }
        }
    }
}
