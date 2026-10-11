import HeadroomKit
import SwiftUI

/// A bar of the share used, or left, with a tick where even pace would be. A window that needs
/// attention gets a wavy bar, as on Android; it stays still when motion is reduced.
struct QuotaBar: View {
    let window: WindowUi
    let color: Color
    var showsLeft = false
    var showsPace = true
    var wavy = false
    /// Waits this long before moving to a new value, so bars can refill one after another.
    var delay = 0.0
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    @Environment(AppModel.self) private var model

    var body: some View {
        ZStack {
            Capsule().fill(.quaternary)
            if wavy {
                TimelineView(.animation(paused: reduceMotion)) { context in
                    WavyFraction(
                        fraction: fraction,
                        phase: reduceMotion ? 0 : context.date.timeIntervalSinceReferenceDate * 2
                    )
                    .stroke(Color.red, style: StrokeStyle(lineWidth: 5, lineCap: .round))
                }
            } else {
                LeadingFraction(fraction: fraction)
                    .fill(window.needsAttention ? .red : color)
            }
            if showsPace, let expected = window.expectedPercent?.doubleValue {
                PaceTick(fraction: (showsLeft ? 100 - expected : expected) / 100)
                    .fill(.primary.opacity(0.6))
            }
        }
        .frame(height: 8)
        .animation(reduceMotion ? nil : .smooth(duration: 0.6).delay(delay), value: fraction)
        .accessibilityHidden(true)
    }

    private var fraction: Double {
        (showsLeft ? window.leftPercent : window.usedPercent) / 100
    }

    private var reduceMotion: Bool {
        systemReduceMotion || model.settings?.reduceMotion == true
    }
}
