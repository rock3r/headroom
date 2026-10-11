import Foundation
import UserNotifications

/// Shows Headroom's notifications while the app is open, and acts on a tap: "Mute this limit"
/// turns the window's alert off, and a tap opens the account, or its sign-in.
final class NotificationRouter: NSObject, UNUserNotificationCenterDelegate, Sendable {
    private let model: AppModel

    @MainActor
    init(model: AppModel) {
        self.model = model
    }

    @MainActor
    func install() {
        UNUserNotificationCenter.current().delegate = self
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        guard let accountId = info["accountId"] as? String else { return }
        let windowId = info["windowId"] as? String
        let signIn = info["signIn"] as? Bool ?? false
        let action = response.actionIdentifier
        await handle(accountId: accountId, windowId: windowId, signIn: signIn, action: action)
    }

    @MainActor
    private func handle(accountId: String, windowId: String?, signIn: Bool, action: String) async {
        if action == NotificationScheduler.muteAction, let windowId {
            // The notification handler must remain alive until DataStore commits the preference.
            try? await model.headroom.accounts.setAlertAndWait(accountId: accountId, windowId: windowId, enabled: false)
        } else if action != UNNotificationDismissActionIdentifier {
            model.open(signIn ? .signIn(accountId: accountId) : .account(accountId))
        }
    }
}
