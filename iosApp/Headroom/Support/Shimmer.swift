import SwiftUI

/// A soft sheen that sweeps across the view while `active`, as the Android bars shimmer while a
/// provider works. It stays off when motion is reduced.
struct Shimmer: ViewModifier {
    let active: Bool
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    @Environment(AppModel.self) private var model

    func body(content: Content) -> some View {
        content.overlay {
            if active && !reduceMotion {
                TimelineView(.animation) { context in
                    GeometryReader { proxy in
                        let progress = context.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.4) / 1.4
                        LinearGradient(colors: [.clear, .white.opacity(0.55), .clear],
                                       startPoint: .leading, endPoint: .trailing)
                            .frame(width: proxy.size.width * 0.4)
                            .offset(x: (proxy.size.width * 1.4) * progress - proxy.size.width * 0.4)
                    }
                }
                .mask(content)
                .allowsHitTesting(false)
            }
        }
    }

    private var reduceMotion: Bool {
        systemReduceMotion || model.settings?.reduceMotion == true
    }
}

extension View {
    func shimmer(_ active: Bool) -> some View {
        modifier(Shimmer(active: active))
    }
}
