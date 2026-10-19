@testable import HeadroomApp
import Testing

@MainActor
struct ReminderDeliveryTests {
    private let reminders = [ReminderDelivery.Reminder(id: "day1", resetKeys: ["a", "b"])]

    @Test func `failed groups remain eligible for retry`() async {
        let keys = await ReminderDelivery.acceptedKeys(
            planned: ["old", "a", "b"], reminders: reminders, canNotify: true
        ) { _ in throw Refused() }
        #expect(keys == ["old"])
    }

    @Test func `permission denial does not mark reminders as sent`() async {
        var calls = 0
        let keys = await ReminderDelivery.acceptedKeys(
            planned: ["old", "a", "b"], reminders: reminders, canNotify: false
        ) { _ in calls += 1 }
        #expect(keys == ["old"])
        #expect(calls == 0)
    }

    @Test func `accepted groups are recorded and a failed group can be retried`() async {
        let keys = await ReminderDelivery.acceptedKeys(
            planned: ["a", "b"], reminders: reminders, canNotify: true
        ) { _ in }
        #expect(keys == ["a", "b"])
    }

    @Test func `unsent sign-in alerts are omitted including reason-suffixed keys`() {
        let keys = NotificationScheduler.acceptedSignInKeys(
            planned: ["claude", "oc:KeyExpired"],
            previous: ["claude"],
            unsentAccountIds: ["oc"]
        )
        #expect(keys == ["claude"])
    }

    @Test func `an unsent sign-in reason upgrade preserves the previously delivered key`() {
        let keys = NotificationScheduler.acceptedSignInKeys(
            planned: ["oc:KeyExpired"],
            previous: ["oc"],
            unsentAccountIds: ["oc"]
        )
        #expect(keys == ["oc"])
    }

    private struct Refused: Error {}
}
