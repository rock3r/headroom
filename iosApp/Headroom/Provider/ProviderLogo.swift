import HeadroomKit
import SwiftUI

/// A provider's logo, scaled to fit, from the SVG path data HeadroomKit carries.
struct ProviderLogo: Shape {
    private let pathData: String
    private let viewport: Double
    private let inset: Double

    init(provider: ProviderUi) {
        pathData = provider.logoPath
        viewport = Double(provider.logoViewport)
        inset = Double(provider.logoInset)
    }

    func path(in rect: CGRect) -> Path {
        let scale = min(rect.width, rect.height) / (viewport + 2 * inset)
        let transform = CGAffineTransform(translationX: rect.minX, y: rect.minY)
            .scaledBy(x: scale, y: scale)
            .translatedBy(x: inset, y: inset)
        return SVGPath.parse(pathData).applying(transform)
    }
}
