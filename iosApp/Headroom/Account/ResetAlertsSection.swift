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
                Toggle("Alert when \(window.label) resets", isOn: binding(for: window))
            }
        } header: {
            Text("Reset alerts")
        } footer: {
            VStack(alignment: .leading) {
                if alertable.contains(where: { $0.kind == "monthly" }) {
                    Text("Monthly windows are off by default")
                }
                if account.windows.contains(where: { $0.kind == "session" }) {
                    Text("Session limits never send alerts.")
                }
            }
        }
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
