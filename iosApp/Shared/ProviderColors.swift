import SwiftUI

/// The colours that identify a provider. Each provider owns a hue, as in the Android app's
/// `ProviderPalette`; saturation and brightness are shared, so no provider looks louder than another.
struct ProviderColors {
    let accent: Color
    let container: Color
    let onContainer: Color

    init(providerId: String, colorScheme: ColorScheme) {
        let hue = (Self.hues[providerId] ?? 0) / 360
        let dark = colorScheme == .dark
        accent = Color(hue: hue, saturation: dark ? 0.45 : 0.62, brightness: dark ? 0.88 : 0.72)
        container = Color(hue: hue, saturation: dark ? 0.55 : 0.18, brightness: dark ? 0.32 : 0.96)
        onContainer = Color(hue: hue, saturation: dark ? 0.20 : 0.75, brightness: dark ? 0.95 : 0.36)
    }

    /// Hues in degrees, spread round the colour wheel.
    private static let hues: [String: Double] = [
        "claude": 45,
        "opencode-go": 100,
        "codex": 155,
        "zai": 195,
        "grok": 235,
        "kimi": 270,
        "copilot": 305,
        "jetbrains": 350,
    ]
}
