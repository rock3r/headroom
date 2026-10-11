import SwiftUI

/// The Stats tab's text for HeadroomKit's ids, worded as in the Android app.
enum StatsTexts {
    static func persona(_ id: String) -> LocalizedStringKey {
        switch id {
        case "earlyBird": "Early bird"
        case "nineToFive": "Nine to five"
        case "eveningHacker": "Evening hacker"
        case "nightOwl": "Night owl"
        default: "Weekend warrior"
        }
    }

    static func period(_ id: String) -> LocalizedStringKey {
        switch id {
        case "fourWeeks": "4 weeks"
        case "threeMonths": "3 months"
        default: "12 months"
        }
    }

    /// "1.5 times the weekly limit".
    static func givenBack(kind: String, times: Double) -> String {
        let count = times.formatted(.number.precision(.fractionLength(0...1)))
        return switch kind {
        case "session": String(localized: "\(count) times the session limit")
        case "daily": String(localized: "\(count) times the daily limit")
        case "monthly": String(localized: "\(count) times the monthly limit")
        default: String(localized: "\(count) times the weekly limit")
        }
    }
}
