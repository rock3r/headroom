import HeadroomKit
import SwiftUI

/// A card's balance or bars, its session bar and its pace chip.
struct CardMeters: View {
    let account: AccountUi
    @Environment(AppModel.self) private var model

    var body: some View {
        let trailingLabels = resetLabels
        let labelWidth = account.separateAllowances && !allowances.isEmpty ? 96.0 : 58.0
        VStack(alignment: .leading, spacing: 8) {
            if account.primary == nil, let balance = account.balance {
                Text("Balance · \(Formats.balance(balance.amount, unit: balance.unit))")
                    .alloFont(.headline)
            } else if account.separateAllowances, !allowances.isEmpty {
                ForEach(allowances) { window in
                    let isPrimary = window.id == account.primary?.id
                    MeterRow(label: window.label, window: window, providerId: account.providerId,
                             trailing: resetLabel(window, countdown: false), trailingLabels: trailingLabels,
                             labelWidth: labelWidth,
                             wavy: isPrimary && window.needsAttention)
                }
            } else if !primaryRows.isEmpty {
                ForEach(primaryRows) { window in
                    let isPrimary = window.id == account.primary?.id
                    MeterRow(label: kindLabel(window.kind), window: window, providerId: account.providerId,
                             trailing: resetLabel(window, countdown: false), trailingLabels: trailingLabels,
                             labelWidth: labelWidth,
                             wavy: isPrimary && window.needsAttention)
                }
            } else {
                Text("No usage data yet.")
                    .alloFont(.subheadline)
                    .foregroundStyle(.secondary)
            }
            if let session = sessionWindow {
                MeterRow(label: kindLabel(session.kind), window: session, providerId: account.providerId,
                         trailing: resetLabel(session, countdown: true), trailingLabels: trailingLabels,
                         labelWidth: labelWidth, showsPace: false)
            }
            if let primary = account.primary {
                PaceChipView(window: primary)
            }
        }
    }

    /// Reset labels reserved across the card so every bar shares one width. Internal for tests.
    var resetLabels: [String] {
        primaryRows.compactMap { resetLabel($0, countdown: false) }
            + [sessionWindow.flatMap { resetLabel($0, countdown: true) }].compactMap { $0 }
    }

    /// The main rows shown above the session bar: every allowance for separate-allowance
    /// providers, otherwise the primary window followed by any monthly window beside it.
    /// Internal for tests.
    var primaryRows: [WindowUi] {
        if account.separateAllowances, !allowances.isEmpty {
            return allowances
        }
        guard let primary = account.primary else { return [] }
        return [primary, account.monthly].compactMap { $0 }
    }

    /// Each window is its own allowance: the main one first, sessions and unlimited ones left out.
    private var allowances: [WindowUi] {
        account.windows.filter { !$0.isUnlimited && $0.kind != "session" }
    }

    /// The session window, shown under the main bars when the main one is not the session.
    /// Internal for tests.
    var sessionWindow: WindowUi? {
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
