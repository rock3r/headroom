import HeadroomKit
import SwiftUI

/// When in the week the user burns quota: one cell per hour, Monday to Sunday, in local time.
struct HeatmapCard: View {
    let heatmap: HeatmapUi?
    let coverageDays: Int?

    var body: some View {
        StatCard(title: "When you burn quota", empty: heatmap == nil ? empty : nil) {
            if let heatmap {
                HStack {
                    Text("Busiest on \(Self.dayName(Int(heatmap.busiestDay))) around \(Self.hourName(Int(heatmap.busiestHour)))")
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text(StatsTexts.persona(heatmap.persona))
                        .font(.caption.bold())
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(.tint.opacity(0.15), in: .capsule)
                }
                HStack(alignment: .top, spacing: 6) {
                    VStack(alignment: .trailing, spacing: 0) {
                        ForEach(1 ... 7, id: \.self) { day in
                            Text(Self.shortDay(day))
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                                .frame(height: 14)
                        }
                    }
                    VStack(spacing: 2) {
                        HeatmapGrid(cells: heatmap.cells.map(\.doubleValue))
                            .frame(height: 7 * 14)
                        HStack {
                            ForEach([0, 6, 12, 18], id: \.self) { hour in
                                Text(Self.hourName(hour))
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(description(heatmap))
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

    private func description(_ heatmap: HeatmapUi) -> String {
        let cells = heatmap.cells.map(\.doubleValue)
        let total = max(cells.reduce(0, +), 0.0001)
        let byDay = (0 ..< 7).map { day in
            let share = cells[(day * 24) ..< min((day + 1) * 24, cells.count)].reduce(0, +) / total
            return "\(Self.dayName(day + 1)) \(Int((share * 100).rounded()))%"
        }.formatted(.list(type: .and))
        return String(localized: "Quota use by day and hour, in local time. Busiest on \(Self.dayName(Int(heatmap.busiestDay))) around \(Self.hourName(Int(heatmap.busiestHour))). By day: \(byDay).")
    }

    /// The day's name, for an ISO day number: 1 is Monday.
    static func dayName(_ isoDay: Int) -> String {
        Calendar.current.standaloneWeekdaySymbols[isoDay % 7]
    }

    static func shortDay(_ isoDay: Int) -> String {
        Calendar.current.veryShortStandaloneWeekdaySymbols[isoDay % 7]
    }

    static func hourName(_ hour: Int) -> String {
        let date = Calendar.current.date(bySettingHour: hour, minute: 0, second: 0, of: .now) ?? .now
        return date.formatted(.dateTime.hour())
    }
}
