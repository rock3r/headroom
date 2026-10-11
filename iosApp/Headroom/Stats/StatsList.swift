import HeadroomKit
import SwiftUI

/// The Stats tab's cards, in the Android order. A card without enough history says what it is
/// waiting for.
struct StatsList: View {
    let stats: StatsUi

    var body: some View {
        List {
            Text(subtitle)
                .foregroundStyle(.secondary)
                .listRowBackground(Color.clear)
            ResetScoreCard(score: stats.resetScore)
            if let usage = stats.resetUsage, !usage.isEmpty {
                ResetUsageCard(periods: usage)
            }
            SharesCard(shares: stats.shares)
            HeatmapCard(heatmap: stats.heatmap, coverageDays: stats.coverageDays?.intValue)
            Section {
                HighlightsRow(closestCall: stats.closestCall, allHits: allHits, biggestDay: stats.biggestDay)
            }
            LeftOverCard(leftOver: stats.leftOver)
            SparklinesCard(sparklines: stats.sparklines)
            Text("Each account counts its main limit: the weekly one, or the longest.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .listRowBackground(Color.clear)
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
