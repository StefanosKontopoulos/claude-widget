// IMPORTANT: Add "com.claudewidget.refreshUsage" to Info.plist under
// BGTaskSchedulerPermittedIdentifiers (array) in the main app target.
// Without this entry, BGTaskScheduler.register() will silently fail.

import BackgroundTasks
import WidgetKit

enum BackgroundRefresh {
    static let taskIdentifier = "com.claudewidget.refreshUsage"

    /// Register the background task handler. Call once at app init (before app enters foreground).
    static func register() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: taskIdentifier,
            using: nil
        ) { task in
            guard let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            handleRefresh(refreshTask)
        }
    }

    /// Schedule the next background refresh. Call when app enters background.
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            print("[BackgroundRefresh] Failed to schedule: \(error)")
        }
    }

    private static func handleRefresh(_ task: BGAppRefreshTask) {
        // Schedule the next refresh immediately so the chain continues
        schedule()

        // Tell WidgetKit to reload, which triggers getTimeline() -> fetchAndStore()
        WidgetCenter.shared.reloadTimelines(ofKind: "ClaudeUsageWidget")

        task.setTaskCompleted(success: true)
    }
}
