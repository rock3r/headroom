import SwiftUI

@main
struct HeadroomApp: App {
    @State private var model = AppModel.live()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            OverviewView()
                .environment(model)
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                // Coming back to the app shows fresh numbers, as on Android.
                model.refresh()
            case .background:
                BackgroundRefresh.schedule()
            default:
                break
            }
        }
        .backgroundTask(.appRefresh(BackgroundRefresh.identifier)) {
            await model.refreshAll()
            BackgroundRefresh.schedule()
        }
    }
}
