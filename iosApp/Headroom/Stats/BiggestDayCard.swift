import HeadroomKit
import SwiftUI

/// The day an account burned the most of its limit.
struct BiggestDayCard: View {
    let biggestDay: BiggestDayUi?

    var body: some View {
        StatCard(title: "Biggest day", empty: biggestDay == nil ? "No busy days yet. This fills in after a few syncs." : nil) {
            if let day = biggestDay {
                Text("\(day.account.name) used \(Int(day.points.rounded()))% of its limit in one day, on \(Self.dayText(day.date)).")
            }
        }
    }

    /// "Sep 27", from HeadroomKit's `yyyy-MM-dd`.
    static func dayText(_ iso: String) -> String {
        guard let date = try? Date(iso, strategy: .iso8601.year().month().day()) else { return iso }
        return date.formatted(.dateTime.month().day())
    }
}
