import SwiftUI

/// The provider signs in with an API key the user pastes.
struct ApiKeyStep: View {
    let providerName: String
    let keyRejected: Bool
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
                    Text("This key does not look right. Check that you copied all of it.")
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
