import Foundation

/// The widgets' words, as the Android widgets say them.
enum WidgetTexts {
    static func percent(_ value: Double) -> String {
        "\(Int(value.rounded()))%"
    }

    /// "62% used" or "38% left".
    static func percentUsed(_ value: Double, left: Bool) -> String {
        left ? String(localized: "\(Int(value.rounded()))% left") : String(localized: "\(Int(value.rounded()))% used")
    }

    static func windowName(_ kind: String) -> String {
        switch kind {
        case "session": String(localized: "session")
        case "daily": String(localized: "daily")
        case "weekly": String(localized: "weekly")
        case "monthly": String(localized: "monthly")
        default: String(localized: "limit")
        }
    }

    /// "Next weekly reset".
    static func nextReset(_ kind: String) -> String {
        String(localized: "Next \(windowName(kind)) reset")
    }

    /// What VoiceOver says about a gauge: "Claude: 62% of the weekly limit used, over pace."
    static func gauge(_ account: WidgetSnapshot.Account, left: Bool) -> String {
        let value = percent(left ? 100 - account.usedPercent : account.usedPercent)
        let window = windowName(account.kind)
        var text = switch (left, account.needsAttention) {
        case (false, false): String(localized: "\(account.name): \(value) of the \(window) limit used.")
        case (false, true): String(localized: "\(account.name): \(value) of the \(window) limit used, over pace.")
        case (true, false): String(localized: "\(account.name): \(value) of the \(window) limit left.")
        case (true, true): String(localized: "\(account.name): \(value) of the \(window) limit left, over pace.")
        }
        if account.resetsAvailable > 0 {
            text += " " + (account.resetsAvailable == 1
                ? String(localized: "1 reset available now.")
                : String(localized: "\(account.resetsAvailable) resets available now."))
        }
        if account.signInExpired {
            text += " " + String(localized: "Sign-in expired, these numbers are out of date.")
        }
        return text
    }

    /// Where a tap on an account leads: its detail, or its sign-in when that expired.
    static func link(_ account: WidgetSnapshot.Account) -> URL {
        account.signInExpired ? DeepLink.signIn(account.id) : DeepLink.account(account.id)
    }
}
