import AppIntents

/// Opens Headroom, from the Control Center control. It is in the app as well as in the widgets,
/// as iOS requires of an intent that opens the app.
struct OpenHeadroomIntent: AppIntent {
    static let title: LocalizedStringResource = "Open Headroom"
    static let openAppWhenRun = true

    func perform() async throws -> some IntentResult {
        .result()
    }
}
