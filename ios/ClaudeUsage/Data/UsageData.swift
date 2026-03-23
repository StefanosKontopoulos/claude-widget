import Foundation

struct UsagePeriod: Codable {
    let utilization: Double
    let resetsAt: String

    /// Progress bar fraction: 0.0 to 1.0
    var fraction: Double {
        min(max(utilization / 100.0, 0.0), 1.0)
    }

    /// Integer percentage for display: 0 to 100
    var percent: Int {
        Int(min(max(utilization, 0.0), 100.0))
    }

    /// Parsed Date from resetsAt string; nil if parsing fails
    var resetDate: Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.date(from: resetsAt)
    }

    /// "Mon 1:00 PM" in the user's local timezone; "soon" if parsing fails
    var resetFormatted: String {
        guard let date = resetDate else { return "soon" }
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE h:mm a"
        formatter.timeZone = .current
        return formatter.string(from: date)
    }
}

struct UsageResponse: Codable {
    let fiveHour: UsagePeriod
    let sevenDay: UsagePeriod
}

struct UsageData: Codable {
    let response: UsageResponse
    var fetchedAt: Date = .now
}
