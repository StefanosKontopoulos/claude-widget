// NOTE: Ensure UsageRepository.swift, UsageData.swift, and CredentialStore.swift
// are added to the ClaudeUsageWidget extension target in Xcode.

import WidgetKit
import SwiftUI
import AppIntents

// MARK: - Timeline Provider

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> SimpleEntry {
        SimpleEntry(date: .now, usageData: nil, hasCredentials: true)
    }

    func getSnapshot(in context: Context, completion: @escaping (SimpleEntry) -> Void) {
        let hasCreds = CredentialStore.loadSessionCookie() != nil
        let data = UsageRepository.getCached()
        completion(SimpleEntry(date: .now, usageData: data, hasCredentials: hasCreds))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SimpleEntry>) -> Void) {
        Task {
            if CredentialStore.loadSessionCookie() != nil {
                try? await UsageRepository.fetchAndStore()
            }
            // Re-check credentials AFTER fetch -- they may have been cleared on auth failure
            let hasCreds = CredentialStore.loadSessionCookie() != nil
            let data = UsageRepository.getCached()
            let entry = SimpleEntry(date: .now, usageData: data, hasCredentials: hasCreds)
            let nextUpdate = Calendar.current.date(byAdding: .minute, value: 15, to: .now)!
            let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
            completion(timeline)
        }
    }
}

// MARK: - Entry

struct SimpleEntry: TimelineEntry {
    let date: Date
    let usageData: UsageData?
    let hasCredentials: Bool

    var isStale: Bool {
        guard let data = usageData else { return false }
        return Date().timeIntervalSince(data.fetchedAt) > 2 * 3600
    }
}

// MARK: - Widget Entry View

struct ClaudeUsageWidgetEntryView: View {
    var entry: Provider.Entry
    @Environment(\.widgetFamily) var widgetFamily

    var body: some View {
        Group {
            if !entry.hasCredentials {
                notLoggedInView
            } else if let data = entry.usageData {
                usageContentView(data: data)
            } else {
                loadingView
            }
        }
        .containerBackground(Color(red: 0.10, green: 0.10, blue: 0.18), for: .widget)
        .widgetURL(URL(string: "claudewidget://open"))
    }

    private var notLoggedInView: some View {
        Text("Sign in to Claude app")
            .font(.caption)
            .foregroundStyle(.white)
            .multilineTextAlignment(.center)
    }

    private var loadingView: some View {
        Text("Loading...")
            .font(.caption)
            .foregroundStyle(.white)
    }

    private func usageContentView(data: UsageData) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            // Title
            HStack {
                Text("Claude Usage" + (entry.isStale ? " (stale)" : ""))
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Color(red: 0.83, green: 0.66, blue: 0.26))
                Spacer()
            }

            // 5-Hour row
            UsageRowView(label: "5 Hour", period: data.response.fiveHour)

            // 7-Day row
            UsageRowView(label: "7 Day", period: data.response.sevenDay)

            // Reset time
            Text("Resets \(data.response.fiveHour.resetFormatted)")
                .font(.system(size: 10))
                .foregroundStyle(.white.opacity(0.7))

            if widgetFamily == .systemMedium {
                HStack {
                    Text("Updated \(formattedTime(data.fetchedAt))")
                        .font(.system(size: 9))
                        .foregroundStyle(.white.opacity(0.5))
                    Spacer()
                    Button(intent: ForceRefreshIntent()) {
                        Image(systemName: "arrow.clockwise")
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.6))
                    }
                    .buttonStyle(.plain)
                }
            } else {
                Text("Updated \(formattedTime(data.fetchedAt))")
                    .font(.system(size: 9))
                    .foregroundStyle(.white.opacity(0.5))
            }
        }
        .padding(12)
    }

    private func formattedTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"
        return formatter.string(from: date)
    }
}

// MARK: - Usage Row

struct UsageRowView: View {
    let label: String
    let period: UsagePeriod

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(label)
                    .font(.system(size: 11))
                    .foregroundStyle(.white)
                Spacer()
                Text("\(period.percent)%")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(progressColor(for: period.fraction))
            }
            ColoredProgressBar(fraction: period.fraction)
        }
    }
}

// MARK: - Custom Progress Bar (GeometryReader-based, not ProgressView)

struct ColoredProgressBar: View {
    let fraction: Double

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 3)
                    .fill(Color.white.opacity(0.15))
                    .frame(height: 6)
                RoundedRectangle(cornerRadius: 3)
                    .fill(progressColor(for: fraction))
                    .frame(width: max(0, geo.size.width * CGFloat(fraction)), height: 6)
            }
        }
        .frame(height: 6)
    }
}

// MARK: - Color Helper

func progressColor(for fraction: Double) -> Color {
    switch fraction {
    case ..<0.70:
        return Color(red: 0.18, green: 0.80, blue: 0.44)   // #2ECC71
    case 0.70..<0.90:
        return Color(red: 0.95, green: 0.61, blue: 0.07)   // #F39C12
    default:
        return Color(red: 0.91, green: 0.30, blue: 0.24)   // #E74C3C
    }
}

// MARK: - Widget Configuration

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
