import HeadroomKit
import SwiftUI

/// When in the week the user burns quota: one cell per hour, Monday to Sunday, in local time.
struct HeatmapCard: View {
    let heatmap: HeatmapUi?
    let coverageDays: Int?
    @ScaledMetric(relativeTo: .caption2) private var rowHeight = 14.0
    @State private var selected: Int?

    var body: some View {
        StatCard(title: "When you use quota", empty: heatmap == nil ? empty : nil) {
            if let heatmap {
                summary(heatmap)
                let cells = heatmap.cells.map(\.doubleValue)
                Text(selectedHour(in: cells) ?? String(localized: "Tap a square to see hourly usage."))
                    .alloFont(.subheadline)
                    .accessibilityHidden(true)
                HStack(alignment: .top, spacing: 6) {
                    VStack(alignment: .trailing, spacing: 0) {
                        ForEach(1 ... 7, id: \.self) { day in
                            Text(Self.shortDay(day))
                                .alloFont(.caption2)
                                .foregroundStyle(.secondary)
                                .frame(height: rowHeight)
                        }
                    }
                    .accessibilityHidden(true)
                    VStack(spacing: 2) {
                        HeatmapGrid(cells: cells, selected: $selected)
                            .frame(height: Double(HeatmapGrid.days) * rowHeight)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(description(heatmap))
                            .accessibilityValue(selectedHour(in: cells) ?? "")
                            .accessibilityIdentifier("Usage heatmap")
                            .accessibilityAdjustableAction { direction in
                                adjustSelection(direction, heatmap: heatmap, cellCount: cells.count)
                            }
                        HStack {
                            ForEach([0, 6, 12, 18], id: \.self) { hour in
                                Text(Self.hourName(hour))
                                    .alloFont(.caption2)
                                    .foregroundStyle(.secondary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                        .accessibilityHidden(true)
                    }
                }
                Text("Local time, all accounts together. Brighter squares mean more quota used.")
                    .alloFont(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func summary(_ heatmap: HeatmapUi) -> some View {
        let busiest = Text("Busiest on \(Self.dayName(Int(heatmap.busiestDay))) around \(Self.hourName(Int(heatmap.busiestHour)))")
            .foregroundStyle(.secondary)
        let persona = Text(StatsTexts.persona(heatmap.persona))
            .alloFont(.caption, weight: 700)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(.tint.opacity(0.15), in: .capsule)
        return ViewThatFits(in: .horizontal) {
            HStack {
                busiest.fixedSize()
                Spacer()
                persona.fixedSize()
            }
            VStack(alignment: .leading, spacing: 6) {
                busiest
                persona
            }
        }
    }

    private func selectedHour(in cells: [Double]) -> String? {
        guard let selected, cells.indices.contains(selected) else { return nil }
        let day = Self.dayName(selected / HeatmapGrid.hours + 1)
        let hour = Self.hourName(selected % HeatmapGrid.hours)
        let usage = Formats.amount(cells[selected])
        return String(localized: "\(day), \(hour): \(usage) percentage points used")
    }

    private func adjustSelection(_ direction: AccessibilityAdjustmentDirection, heatmap: HeatmapUi, cellCount: Int) {
        guard cellCount > 0 else { return }
        let busiest = (Int(heatmap.busiestDay) - 1) * HeatmapGrid.hours + Int(heatmap.busiestHour)
        let next: Int
        switch direction {
        case .increment: next = selected.map { $0 + 1 } ?? busiest
        case .decrement: next = selected.map { $0 - 1 } ?? busiest
        @unknown default: return
        }
        selected = min(max(next, 0), cellCount - 1)
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
        let byDay = (0 ..< HeatmapGrid.days).map { day in
            let share = cells.dropFirst(day * HeatmapGrid.hours).prefix(HeatmapGrid.hours).reduce(0, +) / total
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
        // These are wall-clock buckets, so formatting must not skip or shift an hour on a DST day.
        var format = Date.FormatStyle.dateTime.hour()
        format.timeZone = .gmt
        return Date(timeIntervalSince1970: Double(hour) * 3600).formatted(format)
    }
}
