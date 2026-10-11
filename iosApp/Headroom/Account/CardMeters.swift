import HeadroomKit
import SwiftUI

/// A card's balance or bars, its session bar and its pace chip.
struct CardMeters: View {
    let account: AccountUi
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if account.primary == nil, let balance = account.balance {
                Text("Balance · \(Formats.balance(balance.amount, unit: balance.unit))")
                    .font(.headline)
            } else if account.separateAllowances, !allowances.isEmpty {
                ForEach(allowances) { window in
                    let isPrimary = window.id == account.primary?.id
                    MeterRow(label: window.label, window: window, providerId: account.providerId,
                             trailing: resetLabel(window, countdown: false), labelWidth: 96,
                             wavy: isPrimary && account.needsAttention && !account.signInExpired)
                }
            } else if let primary = account.primary {
                MeterRow(label: kindLabel(primary.kind), window: primary, providerId: account.providerId,
                         trailing: resetLabel(primary, countdown: false),
                         wavy: account.needsAttention && !account.signInExpired)
            } else {
                Text("No usage data yet.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            if let session {
                MeterRow(label: kindLabel(session.kind), window: session, providerId: account.providerId,
                         trailing: resetLabel(session, countdown: true), showsPace: false)
            }
            if let primary = account.primary {
                PaceChipView(window: primary)
            }
        }
    }

    /// Each window is its own allowance: the main one first, sessions and unlimited ones left out.
    private var allowances: [WindowUi] {
        account.windows.filter { !$0.isUnlimited && $0.kind != "session" }
    }

    /// The session window, shown under the main bar when the main one is not the session.
    private var session: WindowUi? {
        account.windows.first { $0.kind == "session" && !$0.isInformational && $0.id != account.primary?.id }
    }

    private func kindLabel(_ kind: String) -> String {
        String(localized: Texts.windowKindResource(kind))
    }

    /// When the window resets. A stale window whose reset time passed has reset since.
    private func resetLabel(_ window: WindowUi, countdown: Bool) -> String? {
        guard let resetsAt = window.resetsAtEpochSeconds?.int64Value else { return nil }
        let date = Date(epochSeconds: resetsAt)
        if account.signInExpired && date <= .now { return String(localized: "Has reset") }
        return countdown ? Formats.countdown(to: date) : Formats.short(date)
    }
}
