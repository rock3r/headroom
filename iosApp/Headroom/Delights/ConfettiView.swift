import SwiftUI

/// A burst of confetti from `origin`, in the provider's colour and the app's accent, that falls and
/// fades in about two seconds, then calls `onFinish`.
struct ConfettiView: View {
    let origin: CGPoint
    let colors: [Color]
    let onFinish: () -> Void
    @State private var start = Date.now
    private let pieces = (0 ..< 70).map { ConfettiPiece(seed: $0) }
    private let duration = 2.2

    var body: some View {
        TimelineView(.animation) { context in
            Canvas { canvas, _ in
                let t = context.date.timeIntervalSince(start)
                for piece in pieces {
                    let position = piece.position(at: t, from: origin)
                    let opacity = max(0, 1 - t / duration)
                    canvas.opacity = opacity
                    var piecePath = Path(roundedRect: CGRect(x: -4, y: -2.5, width: 8, height: 5), cornerRadius: 1.5)
                    piecePath = piecePath.applying(CGAffineTransform(rotationAngle: piece.spin * t + piece.angle))
                    piecePath = piecePath.applying(CGAffineTransform(translationX: position.x, y: position.y))
                    canvas.fill(piecePath, with: .color(colors[piece.colorIndex % max(colors.count, 1)]))
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .task {
            try? await Task.sleep(for: .seconds(duration))
            onFinish()
        }
    }
}

/// One piece: where it flies, how fast it spins, and its colour.
private struct ConfettiPiece {
    let angle: Double
    let speed: Double
    let spin: Double
    let colorIndex: Int

    init(seed: Int) {
        var generator = SeededGenerator(seed: UInt64(seed) &* 2_654_435_761 &+ 1)
        angle = Double.random(in: -Double.pi ... 0, using: &generator)
        speed = Double.random(in: 260 ... 620, using: &generator)
        spin = Double.random(in: -9 ... 9, using: &generator)
        colorIndex = Int.random(in: 0 ..< 4, using: &generator)
    }

    func position(at t: Double, from origin: CGPoint) -> CGPoint {
        let gravity = 900.0
        let drag = exp(-1.6 * t)
        let distance = speed * (1 - drag) / 1.6
        return CGPoint(
            x: origin.x + cos(angle) * distance,
            y: origin.y + sin(angle) * distance + 0.5 * gravity * t * t * 0.6
        )
    }
}

/// The same pieces every time, so a burst looks the same in tests and screenshots.
private struct SeededGenerator: RandomNumberGenerator {
    var state: UInt64

    init(seed: UInt64) {
        state = seed
    }

    mutating func next() -> UInt64 {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }
}
