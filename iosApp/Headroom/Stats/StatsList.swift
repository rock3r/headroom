import HeadroomKit
import SwiftUI

/// The Stats tab's cards. A card without enough history says what it is waiting for.
struct StatsList: View {
    let stats: StatsUi

    var body: some View {
        List {
            Section {
                Text(subtitle)
                    .foregroundStyle(.secondary)
            } footer: {
                Text("Each account counts its main limit: the weekly one, or the longest.")
            }
            ResetScoreCard(score: stats.resetScore)
            SharesCard(shares: stats.shares)
            HeatmapCard(heatmap: stats.heatmap, coverageDays: stats.coverageDays?.intValue)
            ClosestCallCard(closestCall: stats.closestCall, allHits: allHits)
            BiggestDayCard(biggestDay: stats.biggestDay)
            LeftOverCard(leftOver: stats.leftOver)
            SparklinesCard(sparklines: stats.sparklines)
            if let usage = stats.resetUsage, !usage.isEmpty {
                ResetUsageCard(periods: usage)
            }
        }
    }

    private var subtitle: LocalizedStringKey {
        if stats.isDemo { return "Example stats from demo data" }
        switch stats.coverageDays?.intValue {
        case nil: return "No history yet. It builds up with every sync."
        case 0: return "From today's history"
        case let days?: return "From ^[\(days) day](inflect: true) of history"
        }
    }

    /// Every recorded reset hit the limit, so there is no close call to tell.
    private var allHits: Bool {
        guard let score = stats.resetScore else { return false }
        return score.total > 0 && score.clean == 0
    }
}
