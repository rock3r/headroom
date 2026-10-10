import SwiftUI

/// The three tabs of the Android app: Overview, Resets and Stats. Settings opens from the overview.
struct RootView: View {
    @State private var tab = Tab.overview
    @Environment(AppModel.self) private var model

    enum Tab: Hashable {
        case overview, resets, stats
    }

    var body: some View {
        TabView(selection: $tab) {
            SwiftUI.Tab("Overview", systemImage: "square.grid.2x2", value: .overview) {
                OverviewView()
            }
            SwiftUI.Tab("Resets", systemImage: "arrow.counterclockwise.circle", value: .resets) {
                ResetsView()
            }
            SwiftUI.Tab("Stats", systemImage: "chart.pie", value: .stats) {
                StatsView()
            }
        }
        .onChange(of: model.route) { _, route in
            // The overview opens the account a notification is about.
            if route != nil { tab = .overview }
        }
    }
}
