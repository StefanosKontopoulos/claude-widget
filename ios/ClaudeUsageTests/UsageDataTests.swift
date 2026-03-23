import XCTest
@testable import ClaudeUsage

final class UsageDataTests: XCTestCase {

    let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()

    let sampleJSON = """
    {
        "five_hour": {
            "utilization": 42.5,
            "resets_at": "2026-03-23T13:00:00.886839+00:00"
        },
        "seven_day": {
            "utilization": 78.0,
            "resets_at": "2026-03-30T00:00:00.000000+00:00"
        },
        "extra_usage": null,
        "opus": null
    }
    """.data(using: .utf8)!

    func testDecodeUsageResponse() throws {
        let response = try decoder.decode(UsageResponse.self, from: sampleJSON)
        XCTAssertEqual(response.fiveHour.utilization, 42.5, accuracy: 0.001)
        XCTAssertEqual(response.sevenDay.utilization, 78.0, accuracy: 0.001)
    }

    func testFractionNormal() {
        let period = UsagePeriod(utilization: 5.0, resetsAt: "2026-03-23T13:00:00.000000+00:00")
        XCTAssertEqual(period.fraction, 0.05, accuracy: 0.001)
    }

    func testFractionClampedAbove() {
        let period = UsagePeriod(utilization: 150.0, resetsAt: "2026-03-23T13:00:00.000000+00:00")
        XCTAssertEqual(period.fraction, 1.0, accuracy: 0.001)
    }

    func testFractionZero() {
        let period = UsagePeriod(utilization: 0.0, resetsAt: "2026-03-23T13:00:00.000000+00:00")
        XCTAssertEqual(period.fraction, 0.0, accuracy: 0.001)
    }

    func testISO8601WithFractionalSeconds() {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = formatter.date(from: "2026-03-23T13:00:00.886839+00:00")
        XCTAssertNotNil(date, "ISO8601 with fractional seconds must parse without returning nil")
    }

    func testResetFormatted() {
        let period = UsagePeriod(utilization: 50.0, resetsAt: "2026-03-23T13:00:00.886839+00:00")
        XCTAssertFalse(period.resetFormatted.isEmpty)
        XCTAssertNotEqual(period.resetFormatted, "soon", "Parsing failed -- formatter returned fallback")
    }

    func testDecodeWithUnknownKeys() throws {
        XCTAssertNoThrow(try decoder.decode(UsageResponse.self, from: sampleJSON))
    }

    func testEncodeDecodeRoundTrip() throws {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        let period = UsagePeriod(utilization: 50.0, resetsAt: "2026-03-23T13:00:00.000000+00:00")
        let response = UsageResponse(fiveHour: period, sevenDay: period)
        let data = try encoder.encode(response)
        let decoded = try decoder.decode(UsageResponse.self, from: data)
        XCTAssertEqual(decoded.fiveHour.utilization, 50.0, accuracy: 0.001)
    }
}
