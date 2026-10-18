import SwiftUI

/// The compact call to action on a card whose sign-in expired: when its numbers are from.
struct SignInExpiredRow: View {
    let dataFrom: Date?
    var providerId: String? = nil
    var providerName: String = ""
    var usesApiKey = false
    var authReason: String? = nil
    let onSignIn: () -> Void

    private var keyExpired: Bool { authReason == "keyExpired" }

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.bold())
                    .foregroundStyle(.red)
                if keyExpired {
                    Text(Texts.apiKeyExpiredBody(providerId: providerId, providerName: providerName))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Group {
                    if let dataFrom {
                        Text("Last updated \(Formats.age(dataFrom))")
                    } else {
                        Text("Not updated yet")
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(accessibilityDescription)
            Spacer()
            Button(actionTitle, action: onSignIn)
                .buttonStyle(.borderedProminent)
                .tint(.red)
        }
    }

    private var title: LocalizedStringKey {
        if keyExpired { return "API key expired" }
        if usesApiKey { return "API key rejected" }
        return "Sign-in expired"
    }

    private var actionTitle: LocalizedStringKey {
        (usesApiKey || keyExpired) ? "Replace API key" : "Sign in"
    }

    private var accessibilityDescription: String {
        if keyExpired {
            return dataFrom.map { String(localized: "API key expired, data from \(Formats.age($0))") }
                ?? String(localized: "API key expired, no data yet")
        }
        if usesApiKey {
            return dataFrom.map { String(localized: "API key rejected, data from \(Formats.age($0))") }
                ?? String(localized: "API key rejected, no data yet")
        }
        return dataFrom.map { String(localized: "Sign-in expired, data from \(Formats.age($0))") }
            ?? String(localized: "Sign-in expired, no data yet")
    }
}
