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
        // A sidebar on a wide screen, as the Android navigation rail; a tab bar on a phone.
        .tabViewStyle(.sidebarAdaptable)
        .overlay {
            RefreshShimmer(trigger: model.delights.shimmers)
                .ignoresSafeArea()
        }
        .onChange(of: model.route, initial: true) { _, route in
            switch route {
            case .resetsTab:
                tab = .resets
                model.route = nil
            case .statsTab:
                tab = .stats
                model.route = nil
            case .some:
                // The overview opens the account a notification or a widget is about.
                tab = .overview
            case nil:
                break
            }
        }
    }
}
