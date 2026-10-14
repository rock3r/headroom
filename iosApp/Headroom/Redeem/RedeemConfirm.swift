import HeadroomKit
import SwiftUI

/// What a reset gives back, before it is used, as the Android confirmation.
struct RedeemConfirm: View {
    let step: RedeemUiConfirm
    let providerName: String
    let onConfirm: () -> Void
    let onBack: () -> Void
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if step.experimental {
                Text("Experimental")
                    .font(.caption.bold())
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2)
                    .background(.secondary.opacity(0.18), in: .rect(cornerRadius: 8))
            }
            Text(title)
                .font(.title2.bold())
            Text(RedeemTexts.scope(step.pool))
            Group {
                if let expiry = step.pool.soonestExpiryEpochSeconds?.int64Value {
                    Text("Headroom uses the reset that expires first, on \(Formats.long(Date(epochSeconds: expiry))).")
                }
                // Claude's weekly day does not move when a reset is used.
                if step.experimental {
                    Text("Your weekly limit still resets on its usual day.")
                }
            }
            .foregroundStyle(.secondary)
            // When the resets expire at different times, the list shows which ones are left after.
            if step.pool.expiryLines.count > 1 {
                ExpiryList(lines: step.pool.expiryLines)
            }
            if step.pool.anyTime {
                RedeemWarning(text: "You can use this reset before you reach a limit. It cannot be undone.")
            }
            if step.pool.status == "waitingForLimit" {
                RedeemWarning(text: "You can use this reset once you reach a limit. You are not at a limit now.")
            }
            if step.pool.status == "notUsableYet" {
                RedeemWarning(text: "\(providerName) does not let you use this reset yet. Try again later.")
            }
            HStack {
                Button(step.canGoBack ? "Back" : "Not now", action: step.canGoBack ? onBack : onClose)
                Spacer()
                Button("Use a reset", systemImage: "arrow.counterclockwise", action: onConfirm)
                    .buttonStyle(.borderedProminent)
                    .disabled(!step.pool.canUseNow)
            }
            .padding(.top, 8)
        }
    }

    private var title: LocalizedStringKey {
        step.pool.available <= 1 ? "Use this reset?" : "Use 1 of your \(step.pool.available) resets?"
    }
}
