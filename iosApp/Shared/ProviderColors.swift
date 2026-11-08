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

    /// The colour of one product's segment in a window several products share, as the Android
    /// app's `ProviderPalette.partColor`. Slot 0 is the provider's own accent, so the biggest part
    /// matches the ring above it; the others are the Android app's part colours.
    static func part(providerId: String, slot: Int, colorScheme: ColorScheme) -> Color {
        if slot == 0 { return ProviderColors(providerId: providerId, colorScheme: colorScheme).accent }
        let table = colorScheme == .dark ? partsDark : partsLight
        let hex = table[((slot - 1) % table.count + table.count) % table.count]
        return Color(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }

    /// Orange, magenta, green, coral and olive, the same as on Android.
    private static let partsLight = [0xB05A00, 0x9C54A1, 0x34893C, 0xB84C59, 0x857500]
    private static let partsDark = [0xF0AD7F, 0xDFA8E1, 0x95CF96, 0xF7A3A9, 0xCDC072]

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
        "muse": 215,
    ]
}
