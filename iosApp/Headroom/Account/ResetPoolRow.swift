import HeadroomKit
import SwiftUI

/// One pool of resets: its name, what a reset gives back, when the first one expires.
struct ResetPoolRow: View {
    let pool: ResetPoolUi

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline) {
                Text(pool.label)
                Spacer()
                if let status = RedeemTexts.status(pool.status) {
                    Text(status)
                        .font(.caption.bold())
                        .foregroundStyle(.secondary)
                }
                Group {
                    if pool.available > 0 {
                        Text("^[\(pool.available) available](inflect: true)")
                    } else {
                        Text("None right now")
                    }
                }
                .font(.subheadline.monospacedDigit())
            }
            Text(RedeemTexts.scope(pool))
                .font(.footnote)
                .foregroundStyle(.secondary)
            if let expiry = pool.soonestExpiryEpochSeconds?.int64Value {
                Text("The first one expires \(Date(epochSeconds: expiry), format: .relative(presentation: .named))")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
