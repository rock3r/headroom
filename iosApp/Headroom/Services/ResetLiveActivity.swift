import ActivityKit
import Foundation

/// The reset Live Activity: the next reset counts down on the Lock Screen and in the Dynamic Island
/// once it is close, and says so when it happened. It is the iOS take on the Android reset island.
enum ResetLiveActivity {
    /// A Live Activity lasts up to eight hours, so it starts once the reset is this close.
    private static let lead: TimeInterval = 8 * 60 * 60
    /// How long "reset" stays on the Lock Screen.
    private static let afterglow: TimeInterval = 15 * 60

    /// Shows the reset in `next`, or ends the activity when there is none or the user turned it off.
    static func update(next: ResetActivityAttributes.ContentState?, enabled: Bool) async {
        let current = Activity<ResetActivityAttributes>.activities.first
        guard enabled, ActivityAuthorizationInfo().areActivitiesEnabled, let next else {
            if let current { await end(current, hasReset: false) }
            return
        }
        if let current {
            let shown = current.content.state
            if shown.resetsAt <= .now, shown.resetsAt != next.resetsAt {
                // The reset it counted down to happened: say so, then count down to the next one.
                await end(current, hasReset: true)
            } else {
                if shown != next {
                    await current.update(ActivityContent(state: next, staleDate: next.resetsAt))
                }
                return
            }
        }
        let untilReset = next.resetsAt.timeIntervalSinceNow
        guard untilReset > 0, untilReset < lead else { return }
        _ = try? Activity.request(
            attributes: ResetActivityAttributes(),
            content: ActivityContent(state: next, staleDate: next.resetsAt)
        )
    }

    private static func end(_ activity: Activity<ResetActivityAttributes>, hasReset: Bool) async {
        let shown = activity.content.state
        let state = ResetActivityAttributes.ContentState(
            accountId: shown.accountId,
            accountTitle: shown.accountTitle,
            providerId: shown.providerId,
            windowLabel: shown.windowLabel,
            resetsAt: shown.resetsAt,
            hasReset: hasReset
        )
        let dismissal: ActivityUIDismissalPolicy = hasReset ? .after(.now + afterglow) : .immediate
        await activity.end(ActivityContent(state: state, staleDate: nil), dismissalPolicy: dismissal)
    }
}
