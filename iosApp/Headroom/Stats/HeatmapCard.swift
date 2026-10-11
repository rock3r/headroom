import HeadroomKit
import SwiftUI

/// When in the week the user burns quota: one cell per hour, Monday to Sunday.
struct HeatmapCard: View {
    let heatmap: HeatmapUi?
    let coverageDays: Int?

    var body: some View {
        StatCard(title: "When you burn quota", empty: heatmap == nil ? empty : nil) {
            if let heatmap {
                Text(StatsTexts.persona(heatmap.persona))
                    .font(.headline)
                Text("Busiest on \(Self.dayName(Int(heatmap.busiestDay))) around \(Self.hourName(Int(heatmap.busiestHour)))")
                    .foregroundStyle(.secondary)
                HeatmapGrid(cells: heatmap.cells.map(\.doubleValue))
                    .frame(height: 7 * 14)
                    .accessibilityHidden(true)
                Text("Local time, all accounts together. Darker means more quota used.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var empty: LocalizedStringKey {
        if let days = coverageDays, days > 0 {
            "Needs a week of history. ^[\(days) day](inflect: true) so far."
        } else {
            "Needs a week of history."
        }
    }

    /// The day's name, for an ISO day number: 1 is Monday.
    static func dayName(_ isoDay: Int) -> String {
        let symbols = Calendar.current.standaloneWeekdaySymbols
        return symbols[isoDay % 7]
    }

    static func hourName(_ hour: Int) -> String {
        let date = Calendar.current.date(bySettingHour: hour, minute: 0, second: 0, of: .now) ?? .now
        return date.formatted(.dateTime.hour())
    }
}
