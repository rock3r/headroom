import SwiftUI

/// The refresh shimmer: a soft sheen that sweeps once across the screen when new numbers arrive.
struct RefreshShimmer: View {
    static let duration: Duration = .milliseconds(1100)
    let startedAt: ContinuousClock.Instant?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var finished: ContinuousClock.Instant?

    var body: some View {
        GeometryReader { proxy in
            if let startedAt, startedAt != finished, !reduceMotion {
                TimelineView(.animation) { _ in
                    if let progress = Self.progress(from: startedAt, at: .now) {
                        band(in: proxy.size, progress: progress)
                    }
                }
            }
        }
        .clipped()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .task(id: startedAt) {
            guard let startedAt else { return }
            do {
                try await ContinuousClock().sleep(until: startedAt.advanced(by: Self.duration))
                if !Task.isCancelled { finished = startedAt }
            } catch {
                // A new sweep or a disappearing view cancels this deadline.
            }
        }
    }

    static func progress(from start: ContinuousClock.Instant, at now: ContinuousClock.Instant) -> Double? {
        let elapsed = start.duration(to: now)
        guard elapsed >= .zero, elapsed < duration else { return nil }
        return elapsed / duration
    }

    private func band(in size: CGSize, progress: Double) -> some View {
        let sheen: Color = colorScheme == .dark ? .white.opacity(0.28) : .accentColor.opacity(0.18)
        let overhang = size.height * 0.2
        let start = -size.width * 0.6 - overhang
        let end = size.width + overhang
        let eased = progress * progress * (3 - 2 * progress)
        return LinearGradient(colors: [.clear, sheen, .clear], startPoint: .leading, endPoint: .trailing)
            .frame(width: size.width * 0.6, height: size.height * 1.4)
            .rotationEffect(.degrees(12))
            .offset(x: start + (end - start) * eased, y: -overhang)
    }
}
