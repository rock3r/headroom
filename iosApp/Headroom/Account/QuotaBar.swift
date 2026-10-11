import HeadroomKit
import SwiftUI

/// A bar of the share used, with a tick where even pace would be.
struct QuotaBar: View {
    let window: WindowUi
    let color: Color

    var body: some View {
        ZStack {
            Capsule().fill(.quaternary)
            LeadingFraction(fraction: window.usedPercent / 100)
                .fill(window.needsAttention ? .red : color)
            if let expected = window.expectedPercent?.doubleValue {
                PaceTick(fraction: expected / 100)
                    .fill(.primary.opacity(0.6))
            }
        }
        .frame(height: 8)
        .accessibilityHidden(true)
    }
}
