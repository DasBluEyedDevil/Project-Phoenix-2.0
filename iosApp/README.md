# iOS App Setup

The iOS app uses the shared Kotlin Multiplatform module via an Xcode project.

## Prerequisites

- macOS
- Xcode 15 or later
- CocoaPods (optional, for dependency management)

## Setup Instructions

1. Open this directory in Xcode:
   - Create a new Xcode project (iOS App, SwiftUI)
   - Name it "VitruvianProjectPhoenix"
   
2. Configure the shared framework:
   - Add the shared framework from the Kotlin Multiplatform build
   - The framework is built using `./gradlew :shared:assembleXCFramework`

3. Import and use the shared code:
   ```swift
   import shared
   
   // Use the Greeting class
   let greeting = Greeting()
   print(greeting.greet())
   ```

## Building the Shared Framework

From the project root:
```bash
./gradlew :shared:assembleXCFramework
```

The framework will be available at:
`shared/build/XCFrameworks/release/shared.xcframework`

## Notes

- The iOS app provides the native UI layer using SwiftUI
- Business logic is shared via the Kotlin Multiplatform shared module
- BLE functionality will use CoreBluetooth framework for iOS
