import HeadroomKit
import SwiftUI

/// Adding an account, or signing one in again. It shows the step HeadroomKit's sign-in is at; the
/// steps are the same as on Android.
struct SignInView: View {
    enum Mode {
        case add
        case again(accountId: String, providerId: String)
    }

    let mode: Mode
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var browser = BrowserSignIn()

    var body: some View {
        NavigationStack {
            SignInStepView(mode: mode, onReopen: reopenBrowser, onDone: close)
                .navigationTitle(title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel", action: close)
                    }
                }
        }
        .onAppear(perform: startSigningInAgain)
        .onChange(of: browserUrl, initial: true) { _, url in
            showBrowser(url)
        }
        .onDisappear(perform: stop)
    }

    /// The provider's page while the sign-in waits for it, or nil at every other step.
    private var browserUrl: URL? {
        (model.signInStep as? SignInUiBrowser).flatMap { URL(string: $0.authorizationUrl) }
    }

    private var title: LocalizedStringKey {
        switch mode {
        case .add: "Add account"
        case .again: "Sign in again"
        }
    }

    private func startSigningInAgain() {
        if case let .again(accountId, providerId) = mode {
            model.headroom.signIn.start(providerId: providerId, accountId: accountId)
        }
    }

    /// Opens the provider's page as soon as it is ready, and closes it once the redirect came.
    private func showBrowser(_ url: URL?) {
        if let url { browser.open(url) } else { browser.close() }
    }

    private func reopenBrowser() {
        showBrowser(browserUrl)
    }

    private func close() {
        dismiss()
    }

    private func stop() {
        browser.close()
        model.headroom.signIn.cancel()
    }
}
