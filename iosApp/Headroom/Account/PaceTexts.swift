import Foundation
import HeadroomKit
import SwiftUI

/// The pace chart's title and sentences, worded as in the Android app.
enum PaceTexts {
    static func title(_ kind: String) -> LocalizedStringKey {
        switch kind {
        case "weekly": "This week"
        case "monthly": "This month"
        default: "This window"
        }
    }

    static func description(_ chart: ChartUi, showsLeft: Bool) -> Text {
        let above = chart.usedPercent > chart.expectedPercent
        let used = Int(chart.usedPercent.rounded())
        return switch (showsLeft, above) {
        case (false, true): Text("Usage so far: \(used)%, above the even-pace line.")
        case (false, false): Text("Usage so far: \(used)%, at or below the even-pace line.")
        case (true, true): Text("\(100 - used)% left, usage above the even-pace line.")
        case (true, false): Text("\(100 - used)% left, usage at or below the even-pace line.")
        }
    }

    /// Where the window is heading at the current rate.
    static func projection(_ chart: ChartUi, showsLeft: Bool) -> LocalizedStringKey {
        let now = Int64(Date.now.timeIntervalSince1970)
        if let limit = chart.projectedLimitAtEpochSeconds?.int64Value {
            let until = ViewModelsKt.countdown(epochSeconds: limit, nowEpochSeconds: now)
            let before = ViewModelsKt.countdown(epochSeconds: chart.endEpochSeconds, nowEpochSeconds: limit)
            return showsLeft
                ? "At this rate you run out in about \(until), \(before) before the reset."
                : "At this rate you reach 100% in about \(until), \(before) before the reset."
        }
        guard let end = chart.projectedEndPercent?.doubleValue, chart.usedPercent > 0 else {
            return "The window just reset. Nothing used yet."
        }
        let used = Int(end.rounded())
        let left = 100 - used
        return switch (chart.kind, showsLeft) {
        case ("weekly", false): "At this rate you end the week at about \(used)%."
        case ("weekly", true): "At this rate you end the week with about \(left)% left."
        case ("monthly", false): "At this rate you end the month at about \(used)%."
        case ("monthly", true): "At this rate you end the month with about \(left)% left."
        case (_, false): "At this rate you end the window at about \(used)%."
        case (_, true): "At this rate you end the window with about \(left)% left."
        }
    }
}
