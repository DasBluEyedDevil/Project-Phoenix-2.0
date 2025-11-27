import SwiftUI
import shared

@main
struct VitruvianPhoenixApp: App {

    init() {
        // Initialize Koin for dependency injection
        KoinKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
