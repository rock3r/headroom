import HeadroomKit
import SwiftUI

/// The Resets tab's sections.
struct ResetsList: View {
    let tab: ResetsTabUi
    let onUse: (AccountUi) -> Void
    @Environment(AppModel.self) private var model

    var body: some View {
        List {
            if tab.alertWindows > 0 {
                Text("Alerts are on for \(tab.alertsOn) of ^[\(tab.alertWindows) window](inflect: true)")
                    .foregroundStyle(.secondary)
            }
            if !tab.withResets.isEmpty {
                Section("Available resets") {
                    ForEach(tab.withResets) { account in
                        AvailableResetsRow(account: account, canUse: !tab.isDemo) { onUse(account) }
                    }
                }
            }
            if tab.upcoming.isEmpty && tab.history.isEmpty {
                ContentUnavailableView("No weekly or monthly limits to show yet.", systemImage: "calendar")
            }
            if !tab.upcoming.isEmpty {
                Section("Upcoming") {
                    ForEach(tab.upcoming) { reset in
                        UpcomingResetRow(reset: reset, canToggle: !tab.isDemo)
                    }
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
