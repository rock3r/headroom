import SwiftUI

/// The user enters the code at the provider's address, on any device, while the app waits.
struct DeviceCodeStep: View {
    let userCode: String
    let verificationUrl: String
    @Environment(\.openURL) private var openURL

    var body: some View {
        Form {
            Section {
                Text("Go to this address on any device and enter the code.")
                Text(userCode)
                    .font(.largeTitle.monospaced())
                    .bold()
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity)
                Button("Copy code", systemImage: "doc.on.doc", action: copyCode)
                if let url = URL(string: verificationUrl) {
                    Button("Open \(url.host() ?? verificationUrl)", systemImage: "safari") {
                        openURL(url)
                    }
                }
                Label("Waiting for you to confirm", systemImage: "hourglass")
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func copyCode() {
        // SwiftUI has no way to write to the clipboard; UIPasteboard is the API for it.
        UIPasteboard.general.string = userCode
    }
}
