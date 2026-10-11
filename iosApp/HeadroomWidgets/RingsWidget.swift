import SwiftUI
import WidgetKit

/// Weekly and session usage as rings: one account, or up to four in a grid.
struct RingsWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: WidgetCompatibility.ringsKind, intent: AccountsConfiguration.self,
                               provider: SnapshotProvider()) { entry in
            CompatibleRingsView(snapshot: entry.snapshot)
        }
        .configurationDisplayName("Rings")
        .description("Weekly and session usage as rings. One account, or up to four in a grid.")
        .supportedFamilies(WidgetCompatibility.ringsFamilies)
    }
}

private struct CompatibleRingsView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family

    var body: some View {
        if family == .systemSmall || family == .systemMedium {
            RingsView(snapshot: snapshot)
                .containerBackground(.fill.tertiary, for: .widget)
        } else {
            LockScreenView(snapshot: snapshot)
                .containerBackground(.clear, for: .widget)
        }
    }
}

struct RingsView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family

    var body: some View {
        let accounts = Array(snapshot.accounts.prefix(4))
        if snapshot.isDemo || accounts.isEmpty {
            WidgetEmpty(snapshot: snapshot)
        } else if accounts.count == 1, let account = accounts.first {
            single(account)
                .widgetURL(WidgetTexts.link(account))
        } else if family == .systemSmall {
            Grid(horizontalSpacing: 10, verticalSpacing: 6) {
                ForEach(0 ..< (accounts.count + 1) / 2, id: \.self) { row in
                    GridRow {
                        ForEach(accounts.dropFirst(row * 2).prefix(2)) { account in
                            cell(account, lineWidth: 5)
                        }
                    }
                }
            }
            .widgetURL(DeepLink.overview)
        } else {
            HStack(spacing: 12) {
                ForEach(accounts) { account in
                    Link(destination: WidgetTexts.link(account)) {
                        cell(account, lineWidth: 7)
                    }
                }
            }
        }
    }

    private func single(_ account: WidgetSnapshot.Account) -> some View {
        HStack(spacing: 16) {
            RingGauge(account: account, snapshot: snapshot, lineWidth: 11)
            if family == .systemMedium {
                VStack(alignment: .leading, spacing: 4) {
                    Text(account.name)
                        .font(.headline)
                    if let used = account.currentUsedPercent {
                        Text(WidgetTexts.percentUsed(snapshot.shown(used), left: snapshot.showsLeft))
                            .font(.subheadline)
                    } else {
                        Text("Sign in again")
                            .font(.subheadline)
                    }
                    if !account.signInExpired, let session = account.sessionUsedPercent {
                        Text("\(WidgetTexts.windowName("session")) \(WidgetTexts.percent(snapshot.shown(session)))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    if !account.signInExpired, let resetsAt = account.resetsAt {
                        Text("Resets \(resetsAt, style: .relative)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 0)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(WidgetTexts.gauge(account, left: snapshot.showsLeft))
    }

    private func cell(_ account: WidgetSnapshot.Account, lineWidth: Double) -> some View {
        VStack(spacing: 2) {
            RingGauge(account: account, snapshot: snapshot, lineWidth: lineWidth, showsSession: false)
            Text(account.name)
                .font(.caption2)
                .lineLimit(1)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(WidgetTexts.gauge(account, left: snapshot.showsLeft))
    }
}
