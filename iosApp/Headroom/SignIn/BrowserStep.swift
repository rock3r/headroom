import SwiftUI

/// The provider's page is open in the sign-in sheet. Headroom picks up the redirect by itself; the
/// user can paste the code the page shows instead.
struct BrowserStep: View {
    let codeRejected: Bool
    let onReopen: () -> Void
    let onSubmit: (String) -> Void
    @State private var code = ""

    var body: some View {
        Form {
            Section {
                Text("Finish signing in on the page that opens. Headroom picks up the result by itself.")
                Button("Open the sign-in page", systemImage: "safari", action: onReopen)
                Label("Waiting for the browser", systemImage: "hourglass")
                    .foregroundStyle(.secondary)
            }
            Section {
                TextField("Code", text: $code)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button("Continue", action: submit)
                    .disabled(code.trimmingCharacters(in: .whitespaces).isEmpty)
            } header: {
                Text("Or paste the code from the page")
            } footer: {
                if codeRejected {
                    Text("That code did not work. Copy it again from the page.")
                        .foregroundStyle(.red)
                }
            }
        }
    }

    private func submit() {
        onSubmit(code)
    }
}
