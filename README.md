# Vitruvian Project Phoenix - Multiplatform Control App

[![Latest Release](https://img.shields.io/github/v/release/DasBluEyedDevil/Project-Phoenix-2.0?include_prereleases)](https://github.com/DasBluEyedDevil/Project-Phoenix-2.0/releases)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-blue.svg)](https://kotlinlang.org)

A Kotlin Multiplatform application for controlling Vitruvian Trainer workout machines via Bluetooth Low Energy (BLE). This is a multiplatform version of the original [VitruvianProjectPhoenix](https://github.com/DasBluEyedDevil/VitruvianProjectPhoenix) Android app.

## Support the Project

If you find this app useful and want to support its continued development:

**[☕ Buy Me a Coffee](https://buymeacoffee.com/vitruvianredux)**

Your support helps keep the machines running and the code flowing!

---

## Project Overview

This app enables local control of Vitruvian Trainer machines after the company's bankruptcy. It's a community rescue project providing native applications for multiple platforms to keep these machines functional and prevent them from becoming e-waste.

## 🚀 Supported Platforms

| Platform | Status | Notes |
|----------|--------|-------|
| **Android** | 🔄 In Development | BLE support via native APIs |
| **iOS** | 🔄 In Development | BLE support via CoreBluetooth |
| **Desktop (Linux)** | 🔄 In Development | BLE support via BlueZ |
| **Desktop (Windows/macOS)** | 🔄 In Development | BLE support via platform APIs |

## Features

### Core Functionality (Planned)
- **BLE Connectivity**: Reliable connection to Vitruvian Trainer devices
- **All Workout Modes**: Old School, Pump, TUT, TUT Beast, Eccentric-Only, Echo
- **Real-time Monitoring**: Live load, position, velocity, and power metrics
- **Rep Counting**: Accurate rep detection with visual feedback
- **Auto-Stop**: Automatic workout termination based on rep targets

### Enhanced Features (Planned)
- **Exercise Library**: 200+ pre-loaded exercises categorized by muscle group
- **Personal Records**: Automatic PR detection and historical tracking
- **Workout History**: Complete history with metrics stored locally
- **Custom Routines**: Build and save your own workout routines
- **Program Builder**: Create structured multi-exercise programs

### Analytics & Insights (Planned)
- **Muscle Balance Radar**: Visual balance across muscle groups
- **Consistency Gauge**: Monthly workout consistency tracking
- **Volume vs Intensity**: Session comparison charts
- **Total Volume History**: Track volume lifted over time
- **Mode Distribution**: Workout mode usage breakdown

## Technology Stack

| Category | Technology |
|----------|------------|
| **Language** | Kotlin 2.0+ |
| **UI** | Compose Multiplatform |
| **Architecture** | MVVM + Clean Architecture |
| **DI** | Koin (Multiplatform) |
| **BLE** | Platform-specific implementations |
| **Database** | SQLDelight (Multiplatform) |
| **Preferences** | Multiplatform Settings |
| **Async** | Coroutines + Flow |

## Project Structure

```
Project-Phoenix-2.0/
├── shared/                    # Shared Kotlin Multiplatform code
│   └── src/
│       ├── commonMain/        # Common business logic
│       ├── androidMain/       # Android-specific implementations
│       ├── iosMain/           # iOS-specific implementations
│       └── desktopMain/       # Desktop-specific implementations
├── androidApp/                # Android application
├── iosApp/                    # iOS application (Xcode project)
├── desktopApp/                # Desktop application (Linux, Windows, macOS)
├── gradle/                    # Gradle wrapper and version catalog
├── build.gradle.kts           # Root build configuration
└── settings.gradle.kts        # Project settings
```

## Building from Source

### Prerequisites
- JDK 17+
- Android Studio Hedgehog or newer (for Android development)
- Xcode 15+ (for iOS development, macOS only)
- Kotlin 2.0+

### Building the Android App
```bash
./gradlew :androidApp:assembleDebug
```

### Building the Desktop App
```bash
./gradlew :desktopApp:run
```

### Building for iOS
Open `iosApp/iosApp.xcodeproj` in Xcode and build from there.

## Hardware Compatibility

### Vitruvian V-Form Trainer (Euclid / VIT-200)
- **Status:** 🔄 In Development
- **Device Name:** `Vee_*`
- **Max Resistance:** 200 kg (440 lbs)

### Vitruvian Trainer+
- **Status:** 🔄 In Development
- **Max Resistance:** 220 kg (485 lbs)

## Contributing

This is an open-source community project. Contributions welcome!

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Test with real hardware if possible
5. Submit a pull request

## License

MIT License - See [LICENSE](LICENSE) file for details

## Acknowledgments

- Original [VitruvianProjectPhoenix](https://github.com/DasBluEyedDevil/VitruvianProjectPhoenix) Android app
- Original web app developers for reverse-engineering the BLE protocol
- Vitruvian machine owners community for testing and feedback
- JetBrains for Kotlin Multiplatform
- All contributors and supporters

## Support

- **Issues**: [GitHub Issues](https://github.com/DasBluEyedDevil/Project-Phoenix-2.0/issues)
- **Discussions**: [GitHub Discussions](https://github.com/DasBluEyedDevil/Project-Phoenix-2.0/discussions)
- **Support Development**: [Buy Me a Coffee](https://buymeacoffee.com/vitruvianredux)

---

*This app is a community rescue project to keep Vitruvian Trainer machines functional after the company's bankruptcy. It is not affiliated with or endorsed by Vitruvian.*
