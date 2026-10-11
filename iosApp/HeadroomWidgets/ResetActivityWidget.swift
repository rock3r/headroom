import ActivityKit
import SwiftUI
import WidgetKit

/// The reset Live Activity on the Lock Screen and in the Dynamic Island.
struct ResetActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: ResetActivityAttributes.self) { context in
            ResetActivityView(state: context.state.displayed(isStale: context.isStale))
                .padding()
                .widgetURL(DeepLink.account(context.state.accountId))
        } dynamicIsland: { context in
            let state = context.state.displayed(isStale: context.isStale)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Text(state.accountTitle)
                        .font(.headline)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    ResetCountdown(state: state)
                        .font(.headline.monospacedDigit())
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text(state.hasReset ? "\(state.windowLabel) limit reset" : "\(state.windowLabel) limit resets soon")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } compactLeading: {
                Image(systemName: "arrow.counterclockwise")
            } compactTrailing: {
                ResetCountdown(state: state)
                    .monospacedDigit()
                    .frame(maxWidth: 56)
            } minimal: {
                Image(systemName: state.hasReset ? "checkmark" : "arrow.counterclockwise")
            }
            .widgetURL(DeepLink.account(context.state.accountId))
        }
    }
}
