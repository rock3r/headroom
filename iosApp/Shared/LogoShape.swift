import SwiftUI

/// A provider's logo, scaled to fit, from SVG path data in a square `viewport`, with `inset` of
/// empty space on every side so every logo looks the same size.
struct LogoShape: Shape {
    let pathData: String
    let viewport: Double
    let inset: Double

    func path(in rect: CGRect) -> Path {
        let scale = min(rect.width, rect.height) / (viewport + 2 * inset)
        let transform = CGAffineTransform(translationX: rect.minX, y: rect.minY)
            .scaledBy(x: scale, y: scale)
            .translatedBy(x: inset, y: inset)
        return SVGPath.parse(pathData).applying(transform)
    }
}
