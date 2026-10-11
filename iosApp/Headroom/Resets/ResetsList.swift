import HeadroomKit
import SwiftUI

/// The Resets tab's sections, as the Android Resets screen.
struct ResetsList: View {
    let tab: ResetsTabUi
    let onUse: (AccountUi) -> Void
    @Environment(AppModel.self) private var model

    var body: some View {
        List {
            Text(tab.alertWindows == 1
                 ? "Alerts are on for \(tab.alertsOn) of 1 window"
                 : "Alerts are on for \(tab.alertsOn) of \(tab.alertWindows) windows")
                .foregroundStyle(.secondary)
                .listRowBackground(Color.clear)
            if !tab.withResets.isEmpty {
                Section("Available resets") {
                    ForEach(tab.withResets) { account in
                        AvailableResetsRow(account: account, canUse: !tab.isDemo) { onUse(account) }
                    }
                }
            }
            if tab.upcoming.isEmpty {
                Text("No weekly or monthly limits to show yet.")
                    .listRowBackground(Color.clear)
            } else {
                Section("Upcoming") {
                    ForEach(tab.upcoming) { reset in
                        UpcomingResetRow(reset: reset, canToggle: !tab.isDemo)
                    }
                }
                if !tab.history.isEmpty {
                    Section {
                        ForEach(tab.history) { history in
                            ResetHistoryRow(history: history, showsLeft: model.showsLeft)
                        }
                    } header: {
                        Text(model.showsLeft ? "Left when each window reset" : "Used when each window reset")
                    } footer: {
                        ResetHistoryLegend(showsLeft: model.showsLeft)
                    }
                }
            }
        }
    }
}
