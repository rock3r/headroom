import SwiftUI

/// The filled part of a bar as a wave, for a window that needs attention.
struct WavyFraction: Shape {
    var fraction: Double
    var phase: Double
    private let wavelength = 14.0

    var animatableData: Double {
        get { fraction }
        set { fraction = newValue }
    }

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let end = rect.minX + rect.width * min(max(fraction, 0), 1)
        let amplitude = rect.height / 4
        var x = rect.minX + 2.5
        path.move(to: CGPoint(x: x, y: rect.midY + sin(phase) * amplitude))
        while x < end - 2.5 {
            x = min(x + 1, end - 2.5)
            path.addLine(to: CGPoint(x: x, y: rect.midY + sin(x / wavelength * 2 * .pi + phase) * amplitude))
        }
        return path
    }
}
