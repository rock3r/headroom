import ActivityKit
import SwiftUI
import WidgetKit

/// The reset Live Activity on the Lock Screen and in the Dynamic Island.
struct ResetActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: ResetActivityAttributes.self) { context in
            ResetActivityView(state: context.state)
                .padding()
                .widgetURL(DeepLink.account(context.state.accountId))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Text(context.state.accountTitle)
                        .font(.headline)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    ResetCountdown(state: context.state)
                        .font(.headline.monospacedDigit())
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text(context.state.hasReset ? "\(context.state.windowLabel) limit reset" : "\(context.state.windowLabel) limit resets soon")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } compactLeading: {
                Image(systemName: "arrow.counterclockwise")
            } compactTrailing: {
                ResetCountdown(state: context.state)
                    .monospacedDigit()
                    .frame(maxWidth: 56)
            } minimal: {
                Image(systemName: context.state.hasReset ? "checkmark" : "arrow.counterclockwise")
            }
            .widgetURL(DeepLink.account(context.state.accountId))
        }
    }
}
