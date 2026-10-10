import AppIntents
import SwiftUI
import WidgetKit

/// The Control Center control, iOS's take on the Android Quick Settings tile: "Claude · in 2d 4h".
/// A tap opens Headroom.
struct NextResetControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "dev.sebastiano.headroom.next-reset", provider: Provider()) { tile in
            ControlWidgetButton(action: OpenHeadroomIntent()) {
                Label(tile.map { "\($0.name) · \($0.value)" } ?? String(localized: "Headroom"),
                      systemImage: "gauge.with.dots.needle.33percent")
            }
        }
        .displayName("Next reset")
        .description("The next reset of your accounts. Tap to open Headroom.")
    }

    struct Provider: ControlValueProvider {
        var previewValue: WidgetSnapshot.Tile? {
            WidgetSnapshot.preview.nextResetTile
        }

        func currentValue() async throws -> WidgetSnapshot.Tile? {
            let snapshot = WidgetSnapshot.load()
            return snapshot.isDemo ? nil : snapshot.nextResetTile
        }
    }
}
