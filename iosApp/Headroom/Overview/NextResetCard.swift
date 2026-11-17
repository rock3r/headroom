import HeadroomKit
import SwiftUI

/// The reset that comes next, as the Android card: a big countdown, the account and the day, its
/// alert, and the way to every reset.
struct NextResetCard: View {
    let next: NextResetUi
    /// The demo's alerts are examples: there is nothing to switch.
    let canToggleAlert: Bool
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(next.kind == "weekly" ? "Next weekly reset" : "Next reset")
                .alloFont(.subheadline)
                .foregroundStyle(.secondary)
            TimelineView(.everyMinute) { context in
                Text(Formats.countdown(to: resetsAt, now: context.date))
                    .alloFont(.number, size: 38, relativeTo: .largeTitle, tabular: true)
                    .contentTransition(.numericText())
            }
            Text("\(next.accountTitle) · \(Formats.long(resetsAt))")
                .alloFont(.subheadline)
            HStack {
                if canToggleAlert {
                    Button(action: toggleAlert) {
                        Label(next.alertOn ? "Alert on" : "Alert off",
                              systemImage: next.alertOn ? "bell.fill" : "bell.slash")
                    }
                    .buttonStyle(.bordered)
                }
                Spacer()
                Button("All resets", action: openResets)
                    .buttonStyle(.borderless)
            }
            .padding(.top, 4)
        }
        .padding(.vertical, 6)
    }

    private var resetsAt: Date {
        Date(epochSeconds: next.resetsAtEpochSeconds)
    }

    private func toggleAlert() {
        model.headroom.accounts.setAlert(accountId: next.accountId, windowId: next.windowId, enabled: !next.alertOn)
    }

    private func openResets() {
        model.open(.resetsTab)
    }
}
