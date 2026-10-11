import Foundation
import HeadroomKit
import UserNotifications

/// Keeps the pending notifications in line with HeadroomKit's plan: an alert when a weekly limit
/// resets, a reminder a day before a reset expires, and a warning when a sign-in expires.
///
/// iOS cannot wake the app at the reset to check that the limit really reset, as Android's alarms
/// do, so the alerts are scheduled for the reset time the provider announced.
@MainActor
final class NotificationScheduler {
    static let signInCategory = "signIn"
    static let reminderCategory = "reminder"
    static let openAction = "open"
    static let muteAction = "mute"
    static let signInAction = "signIn"

    private let headroom: Headroom
    private let center = UNUserNotificationCenter.current()
    private let defaults = UserDefaults.standard

    init(headroom: Headroom) {
        self.headroom = headroom
    }

    func requestAuthorization() async {
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
    }

    /// The actions of every notification: "Open" and "Mute <account>" on a reset alert, as on
    /// Android. iOS names an action in its category, so each account's alerts get their own.
    private func registerCategories(_ alerts: [ResetAlertUi]) {
        let open = UNNotificationAction(identifier: Self.openAction, title: String(localized: "Open"), options: .foreground)
        var categories: Set<UNNotificationCategory> = [
            UNNotificationCategory(
                identifier: Self.signInCategory,
                actions: [UNNotificationAction(identifier: Self.signInAction, title: String(localized: "Sign in"), options: .foreground)],
                intentIdentifiers: []
            ),
            UNNotificationCategory(identifier: Self.reminderCategory, actions: [open], intentIdentifiers: []),
        ]
        for alert in alerts {
            let mute = UNNotificationAction(identifier: Self.muteAction, title: String(localized: "Mute \(alert.accountName)"))
            categories.insert(UNNotificationCategory(identifier: Self.category(alert), actions: [open, mute], intentIdentifiers: []))
        }
        center.setNotificationCategories(categories)
    }

    private static func category(_ alert: ResetAlertUi) -> String {
        "reset:\(alert.accountId)"
    }

    func reschedule() async {
        guard let plan = try? await headroom.notifications.plan(
            signInNotified: defaults.stringArray(forKey: Keys.signInNotified) ?? [],
            reminded: defaults.stringArray(forKey: Keys.reminded) ?? []
        ) else { return }

        registerCategories(plan.resetAlerts)
        let wanted = plan.resetAlerts.map(\.id) + plan.reminders.map(\.id)
        let pending = await center.pendingNotificationRequests().map(\.identifier)
        let stale = pending.filter { ($0.hasPrefix("reset:") || $0.hasPrefix("reminder:")) && !wanted.contains($0) }
        center.removePendingNotificationRequests(withIdentifiers: stale)

        for alert in plan.resetAlerts {
            try? await center.add(request(alert))
        }
        for reminder in plan.reminders {
            try? await center.add(request(reminder))
        }
        for alert in plan.signInAlerts {
            try? await center.add(request(alert))
        }
        let cancelled = plan.signInCancels.map { "signIn:\($0)" }
        center.removeDeliveredNotifications(withIdentifiers: cancelled)
        center.removePendingNotificationRequests(withIdentifiers: cancelled)

        defaults.set(plan.signInNotified, forKey: Keys.signInNotified)
        defaults.set(plan.reminded, forKey: Keys.reminded)
    }

    private func request(_ alert: ResetAlertUi) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = alert.monthly
            ? String(localized: "\(alert.accountName) monthly limit has reset")
            : String(localized: "\(alert.accountName) weekly limit has reset")
        if let next = alert.nextResetAtEpochSeconds?.int64Value {
            let date = Date(epochSeconds: next).formatted(.dateTime.weekday(.wide).hour().minute())
            content.body = String(localized: "You have your full limit again. Next reset \(date).")
        } else {
            content.body = String(localized: "You have your full limit again.")
        }
        content.sound = .default
        content.categoryIdentifier = Self.category(alert)
        content.threadIdentifier = "resets"
        content.userInfo = ["accountId": alert.accountId, "windowId": alert.windowId]
        return UNNotificationRequest(identifier: alert.id, content: content, trigger: trigger(alert.fireAtEpochSeconds))
    }

    private func request(_ reminder: ReminderUi) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        if reminder.resets.count == 1, let reset = reminder.resets.first {
            content.title = String(localized: "Your \(reset.accountName) reset expires \(Self.when(reset.expiresAtEpochSeconds))")
            content.body = String(localized: "Use it before then, or it's lost.")
        } else {
            let count = reminder.resets.count
            content.title = count == 1
                ? String(localized: "1 reset expires soon")
                : String(localized: "\(count) resets expire soon")
            content.body = reminder.resets
                .map { String(localized: "\($0.accountName): \(Self.when($0.expiresAtEpochSeconds))") }
                .joined(separator: "\n")
        }
        content.sound = .default
        content.threadIdentifier = "reminders"
        content.categoryIdentifier = Self.reminderCategory
        // A tap opens the account whose reset expires first, at its resets, as on Android.
        if let soonest = reminder.resets.min(by: { $0.expiresAtEpochSeconds < $1.expiresAtEpochSeconds }) {
            content.userInfo = ["accountId": soonest.accountId, "resets": true]
        }
        return UNNotificationRequest(identifier: reminder.id, content: content, trigger: trigger(reminder.fireAtEpochSeconds))
    }

    private func request(_ alert: SignInAlertUi) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = alert.label.map { String(localized: "Sign in to \(alert.providerName) again · \($0)") }
            ?? String(localized: "Sign in to \(alert.providerName) again")
        if let synced = alert.syncedAtEpochSeconds?.int64Value {
            let date = Date(epochSeconds: synced).formatted(date: .abbreviated, time: .shortened)
            content.body = String(localized: "Headroom can't update this account until you sign in. The numbers you see are from \(date).")
        } else {
            content.body = String(localized: "Headroom can't update this account until you sign in.")
        }
        content.sound = .default
        content.categoryIdentifier = Self.signInCategory
        content.userInfo = ["accountId": alert.accountId, "signIn": true]
        return UNNotificationRequest(identifier: "signIn:\(alert.accountId)", content: content, trigger: nil)
    }

    private func trigger(_ epochSeconds: Int64) -> UNNotificationTrigger {
        let interval = max(Date(epochSeconds: epochSeconds).timeIntervalSinceNow, 1)
        return UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)
    }

    /// "today at 14:00", "tomorrow at 14:00" or "on 3 Oct at 14:00".
    private static func when(_ epochSeconds: Int64) -> String {
        let date = Date(epochSeconds: epochSeconds)
        let time = date.formatted(date: .omitted, time: .shortened)
        let calendar = Calendar.current
        if calendar.isDateInToday(date) { return String(localized: "today at \(time)") }
        if calendar.isDateInTomorrow(date) { return String(localized: "tomorrow at \(time)") }
        return String(localized: "on \(date.formatted(.dateTime.day().month())) at \(time)")
    }

    private enum Keys {
        static let signInNotified = "notifications.signInNotified"
        static let reminded = "notifications.reminded"
    }
}
