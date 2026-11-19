import HeadroomKit
import SwiftUI

/// One window on the detail screen, as the Android detail lists them: its name, big number, bar,
/// amount, and when it resets or expires.
struct DetailWindowRow: View {
    let window: WindowUi
    let account: AccountUi
    @Environment(\.colorScheme) private var colorScheme
    @Environment(AppModel.self) private var model
    @State private var explaining = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(window.label)
                    .alloFont(.subheadline)
                if !window.isRecognised {
                    Button("About this quota", systemImage: "info.circle") { explaining = true }
                        .labelStyle(.iconOnly)
                        .buttonStyle(.borderless)
                        .popover(isPresented: $explaining) {
                            Text("This is a new quota we don't recognise yet. Headroom shows it for your information, but doesn't know what it's for.")
                                .padding()
                                .frame(idealWidth: 280)
                                .presentationCompactAdaptation(.popover)
                        }
                }
                Spacer()
                Text("\(Int((model.showsLeft ? window.leftPercent : window.usedPercent).rounded()))%")
                    .alloFont(.number, size: 20, relativeTo: .title3)
                    .foregroundStyle(window.needsAttention && !account.signInExpired ? .red : .primary)
                    .accessibilityLabel(Texts.percent(window, left: model.showsLeft))
            }
            if window.parts.isEmpty {
                QuotaBar(
                    window: window,
                    color: ProviderColors(providerId: account.providerId, colorScheme: colorScheme).accent,
                    showsLeft: model.showsLeft,
                    showsPace: isPrimary,
                    wavy: isPrimary && window.needsAttention && !account.signInExpired
                )
            } else {
                SharedWindowParts(
                    window: window,
                    providerId: account.providerId,
                    showsPace: isPrimary
                )
            }
            if let amount = amountLine {
                Text(amount)
                    .alloFont(.footnote)
                    .foregroundStyle(.secondary)
            }
            if let time = timeLine {
                Text(time)
                    .alloFont(.footnote)
                    .foregroundStyle(.secondary)
            }
            if isPrimary && !account.signInExpired {
                PaceChipView(window: window)
            }
        }
        .opacity(account.signInExpired ? 0.55 : 1)
        .padding(.vertical, 4)
    }

    private var isPrimary: Bool { window.id == account.primary?.id }

    /// "12 / 200 credits used", or for a credit "$2 of $10 used".
    private var amountLine: String? {
        guard let used = window.usedAmount?.doubleValue, let limit = window.limitAmount?.doubleValue,
              let unit = window.amountUnit else { return nil }
        let left = max(limit - used, 0)
        if window.kind == "credit" {
            let total = Formats.money(limit, unit: unit)
            return model.showsLeft
                ? String(localized: "\(Formats.money(left, unit: unit)) left of \(total)")
                : String(localized: "\(Formats.money(used, unit: unit)) of \(total) used")
        }
        return model.showsLeft
            ? String(localized: "\(Formats.amount(left)) / \(Formats.amount(limit)) \(unit) left")
            : String(localized: "\(Formats.amount(used)) / \(Formats.amount(limit)) \(unit) used")
    }

    /// When the window resets, as the Android detail words it, or when a credit expires.
    private var timeLine: String? {
        if let expires = window.expiresAtEpochSeconds?.int64Value {
            return String(localized: "Expires \(Formats.date(Date(epochSeconds: expires)))")
        }
        guard let resetsAt = window.resetsAtEpochSeconds?.int64Value else { return nil }
        let date = Date(epochSeconds: resetsAt)
        if account.signInExpired && date <= .now { return String(localized: "Has reset since the last update") }
        let countdown = Formats.countdown(to: date)
        if window.kind == "session" || window.kind == "daily" {
            return String(localized: "Resets in \(countdown) · no alert")
        }
        if date.timeIntervalSinceNow < 86_400 { return String(localized: "Resets in \(countdown)") }
        return String(localized: "Resets \(Formats.long(date))")
    }
}
