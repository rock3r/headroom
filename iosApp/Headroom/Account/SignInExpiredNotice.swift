import SwiftUI

/// Why an account stopped updating, with the way out, as the Android detail's banner.
struct SignInExpiredNotice: View {
    let providerName: String
    var providerId: String? = nil
    var usesApiKey = false
    var authReason: String? = nil
    let onSignIn: () -> Void

    private var keyExpired: Bool { authReason == "keyExpired" }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.headline)
            Text(detail)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button(actionTitle, action: onSignIn)
                .buttonStyle(.borderedProminent)
        }
    }

    private var title: String {
        if keyExpired { return String(localized: "Your \(providerName) API key expired") }
        if usesApiKey { return String(localized: "Your \(providerName) API key was rejected") }
        return String(localized: "Your \(providerName) sign-in expired")
    }

    private var detail: String {
        if keyExpired {
            return Texts.apiKeyExpiredBody(providerId: providerId, providerName: providerName)
        }
        if usesApiKey {
            return String(localized: "\(providerName) rejected this API key. Replace it with a working key to update this account.")
        }
        return String(localized: "Headroom can't update this account until you sign in again. The numbers below are the last it saw.")
    }

    private var actionTitle: LocalizedStringKey {
        (usesApiKey || keyExpired) ? "Replace API key" : "Sign in again"
    }
}
