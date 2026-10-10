import HeadroomKit
import SwiftUI

/// The Stats tab, as on Android: what the usage history says about how the user works.
struct StatsView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        NavigationStack {
            Group {
                if let stats = model.stats {
                    StatsList(stats: stats)
                } else {
                    ProgressView("Reading your history")
                }
            }
            .navigationTitle("Stats")
        }
    }
}
