import HeadroomKit
import SwiftUI

/// A switch per weekly or monthly window, for the notification when it resets.
struct ResetAlertsSection: View {
    let account: AccountUi
    @Environment(AppModel.self) private var model

    var body: some View {
        Section {
            if alertable.isEmpty {
                Text("This account has no weekly or monthly limits to alert on.")
                    .foregroundStyle(.secondary)
            }
            ForEach(alertable) { window in
                Toggle(isOn: binding(for: window)) {
                    Text("Alert when \(window.label) resets")
                    Text(subtitle(window))
                }
            }
        } header: {
            Text("Reset alerts")
        } footer: {
            if account.windows.contains(where: { $0.kind == "session" }) {
                Text("Session limits never send alerts.")
            }
        }
    }

    /// "Alert at Tue 30 Sep, 07:00", a few seconds after the reset; "Off"; or, for a monthly
    /// window still at its default, that monthly windows are off by default.
    private func subtitle(_ window: WindowUi) -> String {
        if window.alertOn, let resetsAt = window.resetsAtEpochSeconds?.int64Value {
            return String(localized: "Alert at \(Formats.long(Date(epochSeconds: resetsAt + 10)))")
        }
        if window.kind == "monthly" { return String(localized: "Monthly windows are off by default") }
        return String(localized: "Off")
    }

    private var alertable: [WindowUi] {
        account.windows.filter(\.canAlert)
    }

    private func binding(for window: WindowUi) -> Binding<Bool> {
        Binding {
            window.alertOn
        } set: { on in
            model.headroom.accounts.setAlert(accountId: account.id, windowId: window.id, enabled: on)
        }
    }
}
