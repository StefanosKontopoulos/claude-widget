import WidgetKit
import SwiftUI

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> SimpleEntry {
        SimpleEntry(date: Date(), message: "Placeholder")
    }

    func getSnapshot(in context: Context, completion: @escaping (SimpleEntry) -> Void) {
        // Canary read — verify App Groups data sharing (populated in Plan 01-04)
        let shared = UserDefaults(suiteName: "group.com.claudewidget")
        let canary = shared?.string(forKey: "canary") ?? "no-data"
        completion(SimpleEntry(date: Date(), message: canary))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SimpleEntry>) -> Void) {
        let shared = UserDefaults(suiteName: "group.com.claudewidget")
        let canary = shared?.string(forKey: "canary") ?? "no-data"
        let entry = SimpleEntry(date: Date(), message: canary)
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 15, to: Date())!
        let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
        completion(timeline)
    }
}

struct SimpleEntry: TimelineEntry {
    let date: Date
    let message: String
}

struct ClaudeUsageWidgetEntryView: View {
    var entry: Provider.Entry

    var body: some View {
        VStack {
            Text("Canary: \(entry.message)")
                .font(.caption)
        }
        .containerBackground(.black, for: .widget)
    }
}

struct ClaudeUsageWidget: Widget {
    let kind: String = "ClaudeUsageWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: Provider()) { entry in
            ClaudeUsageWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Claude Usage")
        .description("Shows your Claude.ai usage limits.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
