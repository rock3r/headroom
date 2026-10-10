import AuthenticationServices
import UIKit

/// Shows a provider's sign-in page in an `ASWebAuthenticationSession`.
///
/// SwiftUI's `webAuthenticationSession` environment action cannot be closed from outside, and the
/// sheet must close as soon as the loopback redirect arrives, so this keeps the session to cancel it.
///
/// It must be this sheet, never Safari: Safari puts Headroom in the background, iOS suspends it,
/// and the loopback listener that receives the provider's redirect stops answering. The redirect,
/// not this session's callback, carries the result; the session only closes when the success page
/// sends the browser to `headroom://`, or when `close()` is called.
@MainActor
final class BrowserSignIn: NSObject, ASWebAuthenticationPresentationContextProviding {
    private var session: ASWebAuthenticationSession?

    /// Opens `url`, closing a page that is already open.
    func open(_ url: URL) {
        close()
        let session = ASWebAuthenticationSession(url: url, callback: .customScheme("headroom")) { _, _ in }
        session.presentationContextProvider = self
        // Keep the browser's cookies: the user may be signed in to the provider already.
        session.prefersEphemeralWebBrowserSession = false
        self.session = session
        session.start()
    }

    func close() {
        session?.cancel()
        session = nil
    }

    nonisolated func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            let scene = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
            return scene?.keyWindow ?? ASPresentationAnchor()
        }
    }
}
