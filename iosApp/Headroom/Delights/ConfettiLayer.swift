import SwiftUI

/// Plays the bursts waiting in the delight center, each from its card, over the overview.
struct ConfettiLayer: View {
    let anchors: [String: Anchor<CGRect>]
    @Environment(AppModel.self) private var model
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        GeometryReader { proxy in
            ForEach(model.delights.bursts) { burst in
                ConfettiView(origin: origin(of: burst, in: proxy), colors: colors(burst)) {
                    model.delights.finished(burst)
                }
            }
        }
        .allowsHitTesting(false)
    }

    private func origin(of burst: DelightCenter.Burst, in proxy: GeometryProxy) -> CGPoint {
        let key = burst.fromNextReset ? DelightAnchors.nextReset : burst.accountId
        if let anchor = anchors[key] ?? anchors[burst.accountId] {
            let frame = proxy[anchor]
            return CGPoint(x: frame.midX, y: frame.minY + 24)
        }
        return CGPoint(x: proxy.size.width / 2, y: proxy.size.height / 3)
    }

    /// The provider's accent twice, then the app's accent and a warm and a cool colour.
    private func colors(_ burst: DelightCenter.Burst) -> [Color] {
        let accent = ProviderColors(providerId: burst.providerId, colorScheme: colorScheme).accent
        return [accent, accent, .accentColor, .orange]
    }
}
