import WidgetKit

/// Existing installations retain these kinds and families when the widget designs change.
enum WidgetCompatibility {
    static let ringsKind = "dev.sebastiano.headroom.usage"
    static let countdownKind = "dev.sebastiano.headroom.countdown"
    static let ringsFamilies: [WidgetFamily] = [
        .systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular, .accessoryInline,
    ]
    static let countdownFamilies: [WidgetFamily] = [.systemSmall, .accessoryRectangular, .accessoryInline]
}
