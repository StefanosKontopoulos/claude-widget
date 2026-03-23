import SwiftUI

struct ContentView: View {
    @State private var canaryStatus: String = "Checking App Groups..."

    var body: some View {
        VStack(spacing: 16) {
            Text("Claude Widget")
                .font(.largeTitle)
                .bold()

            Divider()

            Text("Phase 1 -- Foundation Canary Test")
                .font(.headline)

            Text(canaryStatus)
                .font(.body)
                .foregroundColor(canaryStatus.contains("PASS") ? .green : .orange)
                .multilineTextAlignment(.center)
                .padding()

            Text("Add widget to home screen to verify widget reads this value")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
        .task {
            runCanaryTest()
        }
    }

    private func runCanaryTest() {
        let expected = "canary-phase1-ok"
        UsageRepository.canaryWrite(expected)
        let readBack = UsageRepository.canaryRead()
        if readBack == expected {
            canaryStatus = "PASS: App Group write/read works.\nValue: \(readBack!)"
        } else {
            canaryStatus = "FAIL: Read back '\(readBack ?? "nil")' -- expected '\(expected)'.\nCheck App Groups capability."
        }
    }
}
