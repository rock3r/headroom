import SwiftUI

/// The theme and colours the user picked in Settings, as the Android app's palettes.
enum Appearance {
    /// The colour scheme to force, or nil to follow the system.
    static func colorScheme(_ theme: String?) -> ColorScheme? {
        switch theme {
        case "light": .light
        case "dark": .dark
        default: nil
        }
    }

    /// The accent colour of a palette. `wallpaper` keeps the system accent: iOS has no wallpaper
    /// colours for apps.
    static func tint(_ palette: String?) -> Color? {
        guard let palette, let hex = seeds[palette] else { return nil }
        return Color(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }

    struct Palette: Identifiable {
        let id: String
        let name: LocalizedStringResource
    }

    /// The palettes in Settings order. Their seed colours are the Android app's.
    static let palettes = [
        Palette(id: "wallpaper", name: "System"), Palette(id: "coral", name: "Coral"),
        Palette(id: "tangerine", name: "Tangerine"), Palette(id: "lemon", name: "Lemon"),
        Palette(id: "lime", name: "Lime"), Palette(id: "lagoon", name: "Lagoon"), Palette(id: "sky", name: "Sky"),
        Palette(id: "grape", name: "Grape"), Palette(id: "bubblegum", name: "Bubblegum"),
    ]

    private static let seeds: [String: Int] = [
        "coral": 0xFF6F61, "tangerine": 0xFF8C1A, "lemon": 0xFFD60A, "lime": 0x8BD41A,
        "lagoon": 0x00BFA5, "sky": 0x2E9BFF, "grape": 0x8A4DFF, "bubblegum": 0xFF4FA3,
    ]
}
