import HeadroomKit
import SwiftUI

/// A provider's logo, scaled to fit, from the SVG path data HeadroomKit carries.
struct ProviderLogo: Shape {
    private let logo: LogoShape

    init(provider: ProviderUi) {
        logo = LogoShape(
            pathData: provider.logoPath,
            viewport: Double(provider.logoViewport),
            inset: Double(provider.logoInset)
        )
    }

    func path(in rect: CGRect) -> Path {
        logo.path(in: rect)
    }
}
