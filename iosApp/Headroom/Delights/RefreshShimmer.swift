import SwiftUI

/// The refresh shimmer: a soft sheen that sweeps once across the screen when new numbers arrive.
struct RefreshShimmer: View {
    let trigger: Int
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        GeometryReader { proxy in
            let sheen: Color = colorScheme == .dark ? .white.opacity(0.28) : .accentColor.opacity(0.18)
            LinearGradient(colors: [.clear, sheen, .clear], startPoint: .leading, endPoint: .trailing)
                .frame(width: proxy.size.width * 0.6, height: proxy.size.height * 1.4)
                .rotationEffect(.degrees(12))
                .keyframeAnimator(initialValue: 0.0, trigger: trigger) { band, progress in
                    let overhang = proxy.size.height * 0.2
                    let start = -proxy.size.width * 0.6 - overhang
                    let end = proxy.size.width + overhang
                    band.offset(x: start + (end - start) * progress, y: -proxy.size.height * 0.2)
                } keyframes: { _ in
                    MoveKeyframe(0.0)
                    CubicKeyframe(1.0, duration: 1.1)
                }
        }
        .clipped()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
