import HeadroomKit
import SwiftUI

/// Z.AI's resets need a sign-in to ZCode, separate from the API key the account uses. Its page opens
/// in the same browser sheet as the other sign-ins; once it is done, the redeem starts again.
struct ZCodeSignInStep: View {
    let account: AccountUi
    @Environment(AppModel.self) private var model
    @State private var browser = BrowserSignIn()

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .onChange(of: pageOpening, initial: true) { _, opening in
                if let opening, let url = URL(string: opening.url) { browser.open(url) } else { browser.close() }
            }
            .onChange(of: model.zCodeStep is ZCodeUiDone) { _, done in
                if done { model.headroom.resets.redeem.start(accountId: account.id, askForMore: false) }
            }
            .onDisappear(perform: browser.close)
    }

    @ViewBuilder
    private var content: some View {
        switch model.zCodeStep {
        case is ZCodeUiStarting, is ZCodeUiDone:
            ProgressView()
        case is ZCodeUiWaiting:
            ContentUnavailableView {
                Label("Sign in to ZCode", systemImage: "person.badge.key")
            } description: {
                Text("Sign in to ZCode in your browser. Headroom carries on when you are done.")
            } actions: {
                Button("Open the sign-in page", systemImage: "safari", action: start)
            }
        case let failed as ZCodeUiFailed:
            ContentUnavailableView {
                Label("Sign in to ZCode", systemImage: "exclamationmark.triangle")
            } description: {
                Text(RedeemTexts.zCodeError(failed.error))
            } actions: {
                Button("Try again", action: start)
                    .buttonStyle(.borderedProminent)
            }
        default:
            ContentUnavailableView {
                Label("Sign in to ZCode", systemImage: "person.badge.key")
            } description: {
                Text("\(account.providerName) resets need a separate sign-in to ZCode. Your API key cannot see or use resets.")
            } actions: {
                Button("Sign in to ZCode", action: start)
                    .buttonStyle(.borderedProminent)
            }
        }
    }

    /// The page to show, and how many times it was asked for, so asking again opens it again.
    private var pageOpening: PageOpening? {
        (model.zCodeStep as? ZCodeUiWaiting).map { PageOpening(url: $0.url, opened: Int($0.opened)) }
    }

    private func start() {
        model.headroom.resets.zCode.start(accountId: account.id)
    }

    struct PageOpening: Equatable {
        let url: String
        let opened: Int
    }
}
