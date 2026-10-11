import SwiftUI
import WidgetKit

/// Time left until the next weekly reset of the chosen accounts.
struct CountdownWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: WidgetCompatibility.countdownKind, intent: AccountsConfiguration.self,
                               provider: SnapshotProvider()) { entry in
            CompatibleCountdownView(snapshot: entry.snapshot)
        }
        .configurationDisplayName("Countdown")
        .description("Time left until the next weekly reset of your accounts.")
        .supportedFamilies(WidgetCompatibility.countdownFamilies)
    }
}

private struct CompatibleCountdownView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family

    var body: some View {
        if family == .systemSmall {
            CountdownView(snapshot: snapshot)
                .containerBackground(.fill.tertiary, for: .widget)
        } else {
            legacyCountdown
                .containerBackground(.clear, for: .widget)
        }
    }

    @ViewBuilder private var legacyCountdown: some View {
        if snapshot.isDemo || snapshot.accounts.isEmpty {
            WidgetEmpty(snapshot: snapshot)
        } else if let next = snapshot.nextReset {
            if family == .accessoryInline {
                Text("\(next.accountTitle) · \(next.resetsAt, style: .relative)")
                    .widgetURL(next.destination)
            } else {
                VStack(alignment: .leading) {
                    Text("\(next.accountTitle) · \(WidgetTexts.nextReset(next.kind))")
                        .font(.caption)
                    Text(next.resetsAt, style: .relative)
                        .monospacedDigit()
                }
                .widgetURL(next.destination)
            }
        } else {
            Text("No reset coming up")
                .widgetURL(DeepLink.overview)
        }
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
            .widgetURL(next.destination)
        } else {
            Text("No reset coming up")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .widgetURL(DeepLink.overview)
        }
    }
}
