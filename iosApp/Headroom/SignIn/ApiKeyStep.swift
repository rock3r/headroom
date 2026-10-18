import SwiftUI

/// The provider signs in with an API key the user pastes.
struct ApiKeyStep: View {
    var providerId: String = ""
    let providerName: String
    let keyRejected: Bool
    var rejection: String? = nil
    let onSubmit: (String) -> Void
    @State private var key = ""

    var body: some View {
        Form {
            Section {
                SecureField("API key", text: $key)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button("Save key", action: submit)
                    .disabled(key.isEmpty)
            } header: {
                Text(providerName)
            } footer: {
                if keyRejected {
                    Text(Texts.apiKeyRejection(providerId: providerId, providerName: providerName, rejection: rejection))
                        .foregroundStyle(.red)
                } else {
                    Text("Paste your API key. Headroom keeps it in the Keychain on this device only.")
                }
            }
        }
    }

    private func submit() {
        onSubmit(key)
    }
}
