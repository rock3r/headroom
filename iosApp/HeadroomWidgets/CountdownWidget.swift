import SwiftUI
import WidgetKit

/// Time left until the next weekly reset of the chosen accounts.
struct CountdownWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "dev.sebastiano.headroom.countdown", intent: AccountsConfiguration.self,
                               provider: SnapshotProvider()) { entry in
            CountdownView(snapshot: entry.snapshot)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Countdown")
        .description("Time left until the next weekly reset of your accounts.")
        .supportedFamilies([.systemSmall])
    }
}

struct CountdownView: View {
    let snapshot: WidgetSnapshot

    var body: some View {
        if snapshot.isDemo || snapshot.accounts.isEmpty {
            WidgetEmpty(snapshot: snapshot)
        } else if let next = snapshot.nextReset {
            VStack(alignment: .leading, spacing: 4) {
                Text(WidgetTexts.nextReset(next.kind))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer(minLength: 0)
                Text(next.resetsAt, style: .relative)
                    .font(.title2.bold().monospacedDigit())
                    .minimumScaleFactor(0.6)
                Text("\(next.accountTitle) · \(next.resetsAt.formatted(.dateTime.weekday().hour().minute()))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .widgetURL(DeepLink.account(next.accountId))
        } else {
            Text("No reset coming up")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .widgetURL(DeepLink.overview)
        }
    }
}
