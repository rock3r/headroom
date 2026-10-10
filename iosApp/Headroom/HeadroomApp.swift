import SwiftUI

@main
struct HeadroomApp: App {
    @State private var model: AppModel
    @State private var services: AppServices
    @Environment(\.scenePhase) private var scenePhase

    init() {
        let model = AppModel.live()
        _model = State(initialValue: model)
        _services = State(initialValue: AppServices(model: model))
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .preferredColorScheme(Appearance.colorScheme(model.settings?.theme))
                .tint(Appearance.tint(model.settings?.palette))
                .transaction { transaction in
                    // "Reduce motion" in Settings turns Headroom's own animations off.
                    if model.settings?.reduceMotion == true { transaction.disablesAnimations = true }
                }
                .task(id: ServicesInput(overview: model.overview, settings: model.settings)) {
                    // Waits for the numbers to settle: a sync changes them several times.
                    try? await Task.sleep(for: .seconds(1))
                    guard !Task.isCancelled else { return }
                    await services.update()
                }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                // Coming back to the app shows fresh numbers, as on Android.
                model.refresh()
            case .background:
                BackgroundRefresh.schedule(minutes: model.settings?.syncMinutes?.intValue)
            default:
                break
            }
        }
        .backgroundTask(.appRefresh(BackgroundRefresh.identifier)) {
            await model.refreshAll()
            await services.afterBackgroundSync()
            await BackgroundRefresh.scheduleNext(model)
        }
    }
}
