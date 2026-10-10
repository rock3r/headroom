import SwiftUI
import WidgetKit

/// Before the user added an account.
struct NoAccountsView: View {
    @Environment(\.widgetFamily) private var family

    var body: some View {
        switch family {
        case .accessoryCircular, .accessoryInline:
            Image(systemName: "gauge.with.dots.needle.33percent")
        default:
            Text("Add an account in Headroom")
                .font(.footnote)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
        }
    }
}
