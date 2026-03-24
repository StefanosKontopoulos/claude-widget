import SwiftUI
import BackgroundTasks

@main
struct ClaudeUsageApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        BackgroundRefresh.register()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onChange(of: scenePhase) { oldPhase, newPhase in
                    if newPhase == .background {
                        BackgroundRefresh.schedule()
                    }
                }
        }
    }
}
