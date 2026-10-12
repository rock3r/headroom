import Foundation
import HeadroomKit
import Observation

/// What runs beside the screens: the local notifications, the widgets' snapshot and the reset Live
/// Activity. Each follows the accounts and the settings, as Android's workers and alarms do.
@MainActor
@Observable
final class AppServices {
    @ObservationIgnored private let model: AppModel
    @ObservationIgnored private let notifications: NotificationScheduler
    @ObservationIgnored private let router: NotificationRouter

    init(model: AppModel) {
        self.model = model
        notifications = NotificationScheduler(headroom: model.headroom)
        // Set at launch, so a tap on a notification that launched the app is not missed.
        router = NotificationRouter(model: model)
        router.install()
    }

    /// Brings the notifications, widgets and Live Activity up to date with the app's state.
    func update() async {
        guard let overview = model.overview, let settings = model.settings else { return }
        if !overview.isDemo {
            // Asked once there is an account to notify about, not over the demo data.
            await notifications.requestAuthorization()
        }
        WidgetPublisher.publish(overview: overview, providers: model.providers)
        await ResetLiveActivity.update(next: Self.activityState(overview), enabled: settings.resetIsland)
        await notifications.reschedule()
    }

    private static func activityState(_ overview: OverviewUi) -> ResetActivityAttributes.ContentState? {
        guard !overview.isDemo, let next = overview.nextReset else { return nil }
        return ResetActivityAttributes.ContentState(
            accountId: next.accountId,
            accountTitle: next.accountTitle,
            providerId: overview.accounts.first { $0.id == next.accountId }?.providerId ?? "",
            windowLabel: next.windowLabel,
            resetsAt: Date(epochSeconds: next.resetsAtEpochSeconds),
            hasReset: false
        )
    }

    /// After a background sync: the same, as the app may not come to the front before a reset.
    func afterBackgroundSync() async {
        await update()
    }
}
