// IMPORTANT: This file must be added to BOTH the main app target
// and the ClaudeUsageWidget extension target in Xcode.
// (File Inspector -> Target Membership -> check both)

import AppIntents

struct ForceRefreshIntent: AppIntent {
    static var title: LocalizedStringResource = "Refresh Claude Usage"

    func perform() async throws -> some IntentResult {
        try? await UsageRepository.fetchAndStore()
        return .result()
    }
}
// After .result() returns, WidgetKit automatically calls getTimeline() again.
