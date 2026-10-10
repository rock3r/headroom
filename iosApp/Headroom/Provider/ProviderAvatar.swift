import HeadroomKit
import SwiftUI

/// A provider's logo on a disc of its colour, as the Android app shows it.
struct ProviderAvatar: View {
    let provider: ProviderUi?
    var size = 40.0
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let colors = ProviderColors(providerId: provider?.id ?? "", colorScheme: colorScheme)
        Circle()
            .fill(colors.container)
            .overlay {
                if let provider {
                    ProviderLogo(provider: provider)
                        .fill(colors.onContainer)
                        .padding(size * 0.22)
                }
            }
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}
