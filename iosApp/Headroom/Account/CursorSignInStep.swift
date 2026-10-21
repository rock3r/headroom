import HeadroomKit
import SwiftUI

/// Cursor finishes sign-in on its own page. Headroom keeps polling while the browser sheet is
/// open, then closes it when the shared flow finishes, just as for ZCode.
struct CursorSignInStep: View {
    let step: any ServiceSignInUi
    let onStart: () -> Void
    @State private var browser = BrowserSignIn()

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .onChange(of: pageOpening, initial: true) { _, opening in
                if let opening, let url = URL(string: opening.url) { browser.open(url) } else { browser.close() }
            }
            .onDisappear(perform: browser.close)
    }

    @ViewBuilder
    private var content: some View {
        switch step {
        case is ServiceSignInUiStarting, is ServiceSignInUiDone:
            ProgressView("Connecting Cursor")
        case is ServiceSignInUiWaiting:
            ContentUnavailableView {
                Label("Sign in to Cursor", systemImage: "person.badge.key")
            } description: {
                Text("Sign in to Cursor in your browser. Headroom carries on when you are done.")
            } actions: {
                Button("Open the sign-in page", systemImage: "safari", action: onStart)
            }
        case let failed as ServiceSignInUiFailed:
            ContentUnavailableView {
                Label("Could not connect Cursor", systemImage: "exclamationmark.triangle")
            } description: {
                Text(failure(failed))
            } actions: {
                Button("Try again", action: onStart)
                    .buttonStyle(.borderedProminent)
            }
        default:
            ContentUnavailableView {
                Label("Sign in to Cursor", systemImage: "person.badge.key")
            } description: {
                Text("Connect your Cursor account to see its model pools and Grok Bot usage alongside your Grok limits.")
            } actions: {
                Button("Sign in to Cursor", action: onStart)
                    .buttonStyle(.borderedProminent)
            }
        }
    }

    private func failure(_ failed: ServiceSignInUiFailed) -> LocalizedStringKey {
        switch failed.error {
        case "network": "Headroom could not reach Cursor. Check your connection and try again."
        case "expired": "The sign-in to Cursor took too long. Try again."
        case "differentAccount":
            if let otherAccount = failed.otherAccount {
                "That Cursor account is already connected to \(otherAccount). Sign in to the Cursor account for this one."
            } else if let expectedAccount = failed.expectedAccount {
                "You signed in to a different account. Sign in as \(expectedAccount) to update this one."
            } else {
                "You signed in to a different Cursor account. Try again with the one for this account."
            }
        default: "The sign-in to Cursor did not work. Try again."
        }
    }

    /// Asking to open the same page again must still reopen the browser sheet.
    private var pageOpening: PageOpening? {
        (step as? ServiceSignInUiWaiting).map { PageOpening(url: $0.url, opened: Int($0.opened)) }
    }

    private struct PageOpening: Equatable {
        let url: String
        let opened: Int
    }
}
