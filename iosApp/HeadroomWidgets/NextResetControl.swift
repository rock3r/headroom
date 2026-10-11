import AppIntents
import SwiftUI
import WidgetKit

/// The Control Center control, iOS's take on the Android Quick Settings tile: "Claude · in 2d 4h",
/// or the tightest quota, as chosen in Settings. A tap opens Headroom.
struct NextResetControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "dev.sebastiano.headroom.next-reset", provider: Provider()) { tile in
            ControlWidgetButton(action: OpenHeadroomIntent()) {
                Label(tile.map { "\($0.name) · \($0.value)" } ?? String(localized: "Open Headroom"),
                      systemImage: "gauge.with.dots.needle.33percent")
            }
        }
        .displayName("Headroom")
        .description("The next reset, or your tightest quota. Tap to open Headroom.")
    }

    struct Provider: ControlValueProvider {
        var previewValue: WidgetSnapshot.Tile? {
            WidgetSnapshot.preview.nextResetTile
        }

        func currentValue() async throws -> WidgetSnapshot.Tile? {
            let snapshot = WidgetSnapshot.load()
            guard !snapshot.isDemo else { return nil }
            return ControlSettings.mode == ControlSettings.tightest ? snapshot.tightestTile : snapshot.nextResetTile
        }
    }
}
