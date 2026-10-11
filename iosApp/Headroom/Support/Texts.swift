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
}
