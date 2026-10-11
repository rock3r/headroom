import SwiftUI
import WidgetKit

/// The Lock Screen: a gauge for the tightest limit, rings for up to three accounts with the next
/// reset, or the next reset in a line.
struct LockScreenWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "dev.sebastiano.headroom.lock", intent: AccountsConfiguration.self,
                               provider: SnapshotProvider()) { entry in
            LockScreenView(snapshot: entry.snapshot)
                .containerBackground(.clear, for: .widget)
        }
        .configurationDisplayName("Headroom")
        .description("Your limits and the next reset, on the Lock Screen.")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

struct LockScreenView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family

    var body: some View {
        if snapshot.isDemo || snapshot.accounts.isEmpty {
            WidgetEmpty(snapshot: snapshot)
        } else {
            switch family {
            case .accessoryCircular:
                if let account = snapshot.tightest {
                    gauge(account)
                        .widgetLabel(account.name)
                        .widgetURL(WidgetTexts.link(account))
                }
            case .accessoryRectangular:
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        ForEach(snapshot.accounts.prefix(3)) { account in
                            gauge(account)
                        }
                    }
                    if let next = snapshot.nextReset {
                        Text(footer(next))
                            .font(.caption2)
                            .lineLimit(1)
                    }
                }
                .widgetURL(DeepLink.overview)
            default:
                if let next = snapshot.nextReset {
                    Text(footer(next))
                } else if let account = snapshot.tightest {
                    Text("\(account.name) · \(WidgetTexts.percentUsed(snapshot.shown(account.usedPercent), left: snapshot.showsLeft))")
                }
            }
        }
    }

    private func gauge(_ account: WidgetSnapshot.Account) -> some View {
        Gauge(value: snapshot.shown(account.usedPercent), in: 0 ... 100) {
            Text(account.name.prefix(1))
        } currentValueLabel: {
            Text(account.signInExpired ? "!" : "\(Int(snapshot.shown(account.usedPercent).rounded()))")
        }
        .gaugeStyle(.accessoryCircularCapacity)
        .accessibilityLabel(WidgetTexts.gauge(account, left: snapshot.showsLeft))
    }

    /// "Next weekly reset: Grok, Sun 09:00".
    private func footer(_ next: WidgetSnapshot.NextReset) -> String {
        let when = next.resetsAt.formatted(.dateTime.weekday().hour().minute())
        return String(localized: "\(WidgetTexts.nextReset(next.kind)): \(next.accountTitle), \(when)")
    }
}
