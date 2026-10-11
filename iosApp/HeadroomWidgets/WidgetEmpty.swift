import SwiftUI
import WidgetKit

/// A widget with nothing to show yet.
struct WidgetEmpty: View {
    let snapshot: WidgetSnapshot
    var noSession = false
    @Environment(\.widgetFamily) private var family

    var body: some View {
        switch family {
        case .accessoryCircular, .accessoryInline:
            Image(systemName: "gauge.with.dots.needle.33percent")
                .widgetURL(DeepLink.overview)
        default:
            Text(message)
                .font(.footnote)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .widgetURL(DeepLink.overview)
        }
    }

    private var message: LocalizedStringResource {
        if noSession { return "None of these accounts has a session limit." }
        if snapshot.isDemo { return "Add an account in Headroom." }
        return "No usage data yet. Tap to open Headroom."
    }
}
