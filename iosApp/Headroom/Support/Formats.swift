import Foundation
import HeadroomKit

/// Dates, ages and amounts, formatted as the Android app formats them, in the user's locale.
enum Formats {
    /// A reset time that fits a bar row: "Tue 07:00", or "11 Oct" when it is more than six days away.
    static func short(_ date: Date, now: Date = .now) -> String {
        date.timeIntervalSince(now) > 6 * 86_400
            ? date.formatted(.dateTime.day().month(.abbreviated))
            : date.formatted(.dateTime.weekday(.abbreviated).hour().minute())
    }

    /// A reset time with its day in full: "Tue 30 Sep, 07:00".
    static func long(_ date: Date) -> String {
        date.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated).hour().minute())
    }

    /// A date with its year, for an expiry that can be months away: "5 Nov 2026".
    static func date(_ date: Date) -> String {
        date.formatted(.dateTime.day().month(.abbreviated).year())
    }

    /// The time left until `date`: "15h 28m" or "2d 18h".
    static func countdown(to date: Date, now: Date = .now) -> String {
        ViewModelsKt.countdown(epochSeconds: Int64(date.timeIntervalSince1970),
                               nowEpochSeconds: Int64(now.timeIntervalSince1970))
    }

    /// How long ago, rounded to the nearest unit: "just now", "5 minutes ago", "2 days ago".
    static func age(_ date: Date, now: Date = .now) -> String {
        let minutes = Int(now.timeIntervalSince(date) / 60)
        switch minutes {
        case ..<1: return String(localized: "just now")
        case ..<60: return inflected("^[\(minutes) minute](inflect: true) ago")
        case ..<(24 * 60): return inflected("^[\(minutes / 60) hour](inflect: true) ago")
        default: return inflected("^[\(minutes / (24 * 60)) day](inflect: true) ago")
        }
    }

    /// A localized string with its `^[…](inflect: true)` parts agreed, such as "1 day" or "2 days".
    /// `String(localized:)` leaves them as they are; only an `AttributedString` inflects.
    static func inflected(_ value: String.LocalizationValue) -> String {
        String(AttributedString(localized: value).characters)
    }

    /// "synced just now", "synced 5 min ago", "synced 2 h ago", or "not synced yet".
    static func synced(_ date: Date?, now: Date = .now) -> String {
        guard let date else { return String(localized: "not synced yet") }
        let minutes = Int(now.timeIntervalSince(date) / 60)
        switch minutes {
        case ..<1: return String(localized: "synced just now")
        case ..<60: return String(localized: "synced \(minutes) min ago")
        default: return String(localized: "synced \(minutes / 60) h ago")
        }
    }

    /// A balance as money when its unit is a currency code, such as "$12.50"; otherwise the amount
    /// with two decimals and the unit.
    static func balance(_ amount: Double, unit: String) -> String {
        if isCurrency(unit) {
            return amount.formatted(.currency(code: unit.uppercased()))
        }
        return "\(amount.formatted(.number.precision(.fractionLength(2)))) \(unit)"
    }

    /// An amount with up to two decimals: "200", "9.99". Above zero, it never rounds to zero.
    static func amount(_ amount: Double) -> String {
        let shown = amount > 0 && amount < 0.01 ? 0.01 : amount
        return shown.formatted(.number.precision(.fractionLength(0 ... 2)))
    }

    /// Money in the currency `unit` names: "$102", or "$102.50" when it has cents.
    static func money(_ amount: Double, unit: String) -> String {
        guard isCurrency(unit) else { return "\(Self.amount(amount)) \(unit)" }
        let cents = amount.truncatingRemainder(dividingBy: 1) == 0 ? 0 : 2
        return amount.formatted(.currency(code: unit.uppercased()).precision(.fractionLength(cents)))
    }

    private static func isCurrency(_ unit: String) -> Bool {
        Locale.commonISOCurrencyCodes.contains(unit.uppercased())
    }
}
