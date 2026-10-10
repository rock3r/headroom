import SwiftUI
import WidgetKit

/// Counts down to the next reset.
struct CountdownWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "dev.sebastiano.headroom.countdown", provider: SnapshotProvider()) { entry in
            CountdownWidgetView(snapshot: entry.snapshot)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Next reset")
        .description("Counts down to the next weekly reset of your accounts.")
        .supportedFamilies([.systemSmall, .accessoryRectangular, .accessoryInline])
    }
}
