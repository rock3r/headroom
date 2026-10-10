import BackgroundTasks

/// Syncs the accounts in the background. SwiftUI's `backgroundTask` modifier runs the sync; this
/// asks iOS for the next run, no sooner than the sync frequency chosen in Settings. iOS decides when
/// it actually happens. The identifier must be listed in Info.plist.
enum BackgroundRefresh {
    static let identifier = "dev.sebastiano.headroom.refresh"

    /// Asks for a sync in `minutes`, or cancels it when the user only wants syncs on open.
    static func schedule(minutes: Int?) {
        guard let minutes else {
            BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: identifier)
            return
        }
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: TimeInterval(minutes * 60))
        try? BGTaskScheduler.shared.submit(request)
    }

    static func scheduleNext(_ model: AppModel) async {
        let minutes = await MainActor.run { model.settings?.syncMinutes?.intValue }
        schedule(minutes: minutes)
    }
}
