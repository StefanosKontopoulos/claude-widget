// NOTE: Ensure UsageRepository.swift, UsageData.swift, and CredentialStore.swift
// are added to the ClaudeUsageWidget extension target in Xcode.

import WidgetKit
import SwiftUI

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> SimpleEntry {
        SimpleEntry(date: .now, usageData: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (SimpleEntry) -> Void) {
        let data = UsageRepository.getCached()
        completion(SimpleEntry(date: .now, usageData: data))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SimpleEntry>) -> Void) {
        Task {
            // Attempt live fetch; ignore errors (fall back to cache)
            try? await UsageRepository.fetchAndStore()
            let data = UsageRepository.getCached()
            let entry = SimpleEntry(date: .now, usageData: data)
            let nextUpdate = Calendar.current.date(byAdding: .minute, value: 15, to: .now)!
            let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
            completion(timeline)
        }
    }
}

struct SimpleEntry: TimelineEntry {
    let date: Date
    let usageData: UsageData?
}

// TODO: Phase 5 will replace this with the full widget UI
struct ClaudeUsageWidgetEntryView: View {
    var entry: Provider.Entry

    var body: some View {
        VStack {
            if let data = entry.usageData {
                Text("5h: \(data.response.fiveHour.percent)%")
                    .font(.caption)
                Text("7d: \(data.response.sevenDay.percent)%")
                    .font(.caption)
            } else {
                Text("Loading...")
                    .font(.caption)
            }
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
