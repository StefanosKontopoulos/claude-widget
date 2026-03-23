import SwiftUI

struct ContentView: View {
    @State private var isLoggedIn = false
    @State private var showLogin = false
    @State private var orgIdDisplay: String = ""
    @State private var cookieDisplay: String = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("Claude Widget")
                    .font(.largeTitle)
                    .bold()

                Divider()

                if isLoggedIn {
                    VStack(spacing: 8) {
                        Text("Logged in")
                            .font(.headline)
                            .foregroundColor(.green)
                        Text("Org ID: \(orgIdDisplay)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text("Cookie: \(cookieDisplay)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                } else {
                    Text("Not logged in")
                        .font(.headline)
                        .foregroundColor(.orange)

                    Button("Sign in to Claude") {
                        showLogin = true
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
            .padding()
            .onAppear {
                checkCredentials()
            }
            .sheet(isPresented: $showLogin) {
                NavigationStack {
                    LoginView(onLoginSuccess: {
                        showLogin = false
                        checkCredentials()
                    })
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Cancel") {
                                showLogin = false
                            }
                        }
                    }
                }
            }
        }
    }

    private func checkCredentials() {
        let cookie = CredentialStore.loadSessionCookie()
        let orgId = CredentialStore.loadOrgId()
        isLoggedIn = (cookie != nil && orgId != nil)
        if let orgId {
            orgIdDisplay = String(orgId.prefix(8)) + "..."
        }
        if let cookie {
            cookieDisplay = String(cookie.prefix(20)) + "..."
        }
        if !isLoggedIn {
            showLogin = true
        }
    }
}
