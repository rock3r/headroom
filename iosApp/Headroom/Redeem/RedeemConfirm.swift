import HeadroomKit
import SwiftUI

/// What a reset gives back, before it is used.
struct RedeemConfirm: View {
    let step: RedeemUiConfirm
    let onConfirm: () -> Void
    let onBack: () -> Void
    let onClose: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if step.experimental {
                    Text("Experimental")
                        .font(.caption.bold())
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(.orange.opacity(0.2), in: .capsule)
                }
                Text(title)
                    .font(.title2.bold())
                Text(RedeemTexts.scope(step.pool))
                if step.pool.available > 1, let expiry = step.pool.soonestExpiryEpochSeconds?.int64Value {
                    Text("Headroom uses the reset that expires first, on \(Date(epochSeconds: expiry), format: .dateTime.month().day().hour().minute()).")
                        .foregroundStyle(.secondary)
                }
                Text("You can use this reset before you reach a limit. It cannot be undone.")
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 8) {
                Button(action: onConfirm) {
                    Text("Use a reset")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                HStack {
                    if step.canGoBack {
                        Button("Back", action: onBack)
                    }
                    Spacer()
                    Button("Not now", action: onClose)
                }
            }
            .padding()
        }
    }

    private var title: LocalizedStringKey {
        step.pool.available <= 1 ? "Use this reset?" : "Use 1 of your \(step.pool.available) resets?"
    }
}
