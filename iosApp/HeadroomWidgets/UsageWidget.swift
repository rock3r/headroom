import SwiftUI
import WidgetKit

/// How much of each account's main limit is used, or left: rings on the Home Screen, a gauge on
/// the Lock Screen.
struct UsageWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "dev.sebastiano.headroom.usage", provider: SnapshotProvider()) { entry in
            UsageWidgetView(snapshot: entry.snapshot)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Usage")
        .description("How much of each limit you have used, or have left.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}
