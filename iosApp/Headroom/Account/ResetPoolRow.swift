import HeadroomKit
import SwiftUI

/// One pool of resets: its name and count, what a reset gives back, and when each one expires.
struct ResetPoolRow: View {
    let pool: ResetPoolUi

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline) {
                Text(pool.label)
                Spacer()
                if let status = RedeemTexts.status(pool.status) {
                    Text(status)
                        .alloFont(.caption, weight: 700)
                        .foregroundStyle(.secondary)
                }
                Text(count)
                    .alloFont(.subheadline)
                    .accessibilityLabel(countDescription)
            }
            Text(RedeemTexts.scope(pool))
                .alloFont(.footnote)
                .foregroundStyle(.secondary)
            ExpiryList(lines: pool.expiryLines)
        }
    }

    private var count: String {
        if let total = pool.total?.intValue { return String(localized: "\(pool.available) of \(total)") }
        if pool.available > 0 { return String(localized: "\(pool.available) available") }
        return String(localized: "None right now")
    }

    private var countDescription: String {
        guard let total = pool.total?.intValue else { return count }
        return total == 1
            ? String(localized: "\(pool.available) of 1 reset left")
            : String(localized: "\(pool.available) of \(total) resets left")
    }
}
