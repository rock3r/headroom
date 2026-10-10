import HeadroomKit
import SwiftUI

/// The redeem sheet's text for HeadroomKit's ids, worded as in the Android app.
enum RedeemTexts {
    /// What one reset of `pool` gives back.
    static func scope(_ pool: ResetPoolUi) -> LocalizedStringKey {
        switch pool.scope {
        case "codex": "Resets your current 5-hour and weekly limits."
        case "codexMonthly": "Resets your monthly limit."
        case "grok": "Resets your current weekly usage pool."
        case "zaiFiveHour": "Resets your current 5-hour limit."
        case "zaiWeek": "Resets your current weekly limit."
        case "windows" where !pool.scopeWindows.isEmpty:
            "Refills \(pool.scopeWindows.formatted(.list(type: .and)))."
        default: "Resets your current usage limits."
        }
    }

    /// Why a pool cannot be used now, or nil when it can.
    static func status(_ id: String) -> LocalizedStringKey? {
        switch id {
        case "queued": "Queued"
        case "paused": "Paused"
        case "waitingForLimit": "At a limit only"
        case "notUsableYet": "Not yet"
        default: nil
        }
    }

    static func outcomeTitle(_ id: String) -> LocalizedStringKey {
        switch id {
        case "success": "Usage reset"
        case "nothingToReset": "Nothing to reset"
        case "noCredit": "No resets left"
        case "cooldown": "Another reset is running"
        case "ineligible": "Reset no longer available"
        case "unconfirmed": "Not confirmed yet"
        case "rateLimited": "Too many requests"
        case "signInAgain": "Sign in again"
        case "unsupported": "Resets are not available"
        default: "Could not reset"
        }
    }

    static func outcomeBody(_ finished: RedeemUiFinished, provider: String) -> LocalizedStringKey {
        switch finished.outcome {
        case "success":
            if let left = finished.resetsLeft?.intValue {
                left == 0 ? "No resets left." : "^[\(left) reset](inflect: true) left."
            } else {
                ""
            }
        case "nothingToReset": "Your usage is already low, so no reset was used."
        case "noCredit": "You have no resets to use right now."
        case "cooldown":
            "A reset was just started for this account, so nothing was used. Check your usage in a moment."
        case "ineligible": "This reset cannot be used any more. Nothing was used."
        case "unconfirmed":
            "\(provider) did not confirm the reset. It may have worked. Check again to find out. Headroom will not send it a second time."
        case "rateLimited": "\(provider) asked Headroom to slow down. Nothing was used."
        case "signInAgain": "\(provider) no longer accepts your sign-in. Sign in again, then try the reset."
        case "unsupported": "This account cannot use resets."
        default:
            "Headroom could not reach \(provider). Trying again is safe: it can never use two resets for one try."
        }
    }

    static func answerTitle(_ id: String) -> LocalizedStringKey {
        switch id {
        case "granted": "You got a reset card"
        case "notYet": "No reset card yet"
        case "throttled": "Too many requests"
        case "unsupported": "Resets are not available"
        default: "Could not ask"
        }
    }

    static func answerBody(_ id: String, provider: String) -> LocalizedStringKey {
        switch id {
        case "granted": "You can use it now, or keep it for later."
        case "notYet": "\(provider) gives reset cards off-peak, and only once your usage is high enough."
        case "throttled": "\(provider) asked Headroom to slow down. Try again in a while."
        case "unsupported": "This account cannot use resets."
        default: "Headroom could not reach \(provider). Try again later."
        }
    }

    static func zCodeError(_ id: String) -> LocalizedStringKey {
        switch id {
        case "network": "Headroom could not reach ZCode. Check your connection and try again."
        case "expired": "The sign-in to ZCode took too long. Try again."
        default: "The sign-in to ZCode did not work. Try again."
        }
    }
}
