import SwiftUI

/// The refresh shimmer: a soft sheen that sweeps once across the screen when new numbers arrive.
struct RefreshShimmer: View {
    let trigger: Int
    @State private var progress = -0.4

    var body: some View {
        GeometryReader { proxy in
            LinearGradient(colors: [.clear, .white.opacity(0.28), .clear], startPoint: .topLeading, endPoint: .bottomTrailing)
                .frame(width: proxy.size.width * 0.6, height: proxy.size.height * 1.4)
                .rotationEffect(.degrees(12))
                .offset(x: proxy.size.width * progress, y: -proxy.size.height * 0.2)
                .blendMode(.plusLighter)
                .opacity(progress > -0.4 && progress < 1.2 ? 1 : 0)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onChange(of: trigger) {
            progress = -0.6
            withAnimation(.easeInOut(duration: 1.1)) { progress = 1.3 }
        }
    }
}
