/// Only reminders accepted by iOS may suppress a later retry.
@MainActor
enum ReminderDelivery {
    struct Reminder {
        let id: String
        let resetKeys: [String]
    }

    static func acceptedKeys(
        planned: [String], reminders: [Reminder], canNotify: Bool,
        submit: (String) async throws -> Void
    ) async -> [String] {
        var unsent: Set<String> = []
        for reminder in reminders {
            if canNotify, (try? await submit(reminder.id)) != nil { continue }
            unsent.formUnion(reminder.resetKeys)
        }
        return planned.filter { !unsent.contains($0) }
    }
}
