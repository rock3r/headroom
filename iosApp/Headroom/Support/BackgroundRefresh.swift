import BackgroundTasks

/// Syncs the accounts in the background. SwiftUI's `backgroundTask` modifier runs the sync; this
/// asks iOS for the next run. iOS decides when it happens, no sooner than `interval`, which matches
/// the Android app's default sync frequency. The identifier must be listed in Info.plist.
enum BackgroundRefresh {
    static let identifier = "dev.sebastiano.headroom.refresh"
    private static let interval: TimeInterval = 15 * 60

    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: interval)
        try? BGTaskScheduler.shared.submit(request)
    }
}
