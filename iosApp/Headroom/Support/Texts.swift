import HeadroomKit
import SwiftUI

/// The text for HeadroomKit's ids, worded as in the Android app. They are `LocalizedStringKey`s, so
/// the String Catalog picks them up.
enum Texts {
    /// Why an account's last sync failed.
    static func syncError(_ id: String) -> LocalizedStringKey {
        switch id {
        case "auth": "Sign-in expired. Sign in again to update."
        case "access": "This plan has no usage data."
        case "rateLimited": "The provider asked us to slow down. Showing the last numbers."
        case "network": "No connection. Showing the last numbers."
        case "parse": "The provider changed its format. Showing the last numbers."
        default: "Could not update. Showing the last numbers."
        }
    }

    /// Why a sign-in stopped.
    static func signInError(_ id: String) -> LocalizedStringKey {
        switch id {
        case "denied": "The sign-in was declined on the provider's page."
        case "expired": "The sign-in took too long and expired."
        case "network": "There was no connection. Check it and try again."
        case "differentAccount": "You signed in to a different account. Sign in as the same account to update this one."
        default: "Something went wrong. Try again."
        }
    }

    static func pace(_ id: String) -> LocalizedStringKey {
        switch id {
        case "over": "Over pace"
        case "under": "Under pace"
        default: "On pace"
        }
    }

    static func windowKind(_ id: String) -> LocalizedStringKey {
        switch id {
        case "session": "Session"
        case "daily": "Daily"
        case "weekly": "Weekly"
        case "monthly": "Monthly"
        case "credit": "Credit"
        default: "Limit"
        }
    }

    /// What the big number on a card measures: "weekly used", "session left".
    static func quotaLabel(_ kind: String, left: Bool) -> LocalizedStringKey {
        switch (kind, left) {
        case ("weekly", false): "weekly used"
        case ("monthly", false): "monthly used"
        case ("daily", false): "daily used"
        case ("session", false): "session used"
        case ("credit", false): "credit used"
        case (_, false): "used"
        case ("weekly", true): "weekly left"
        case ("monthly", true): "monthly left"
        case ("daily", true): "daily left"
        case ("session", true): "session left"
        case ("credit", true): "credit left"
        case (_, true): "left"
        }
    }

    /// "62% used" or "38% left".
    static func percent(_ window: WindowUi, left: Bool) -> String {
        left
            ? String(localized: "\(Int(window.leftPercent.rounded()))% left")
            : String(localized: "\(Int(window.usedPercent.rounded()))% used")
    }

    static func windowKindResource(_ id: String) -> LocalizedStringResource {
        switch id {
        case "session": "Session"
        case "daily": "Daily"
        case "weekly": "Weekly"
        case "monthly": "Monthly"
        case "credit": "Credit"
        default: "Limit"
        }
    }
}
