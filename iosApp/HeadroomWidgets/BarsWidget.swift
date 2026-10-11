import SwiftUI
import WidgetKit

/// One bar per account, with a tick where even pace would be.
struct BarsWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "dev.sebastiano.headroom.bars", intent: AccountsConfiguration.self,
                               provider: SnapshotProvider()) { entry in
            BarsView(snapshot: entry.snapshot)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Bars")
        .description("One bar per account, with a tick where even pace would be.")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

struct BarsView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        if snapshot.isDemo || snapshot.accounts.isEmpty {
            WidgetEmpty(snapshot: snapshot)
        } else {
            let limit = family == .systemLarge ? 7 : 3
            let shown = snapshot.accounts.count > limit ? limit - 1 : limit
            VStack(alignment: .leading, spacing: family == .systemLarge ? 12 : 8) {
                ForEach(snapshot.accounts.prefix(shown)) { account in
                    Link(destination: WidgetTexts.link(account)) {
                        row(account)
                    }
                }
                let more = snapshot.accounts.count - shown
                if more > 0 {
                    Link(destination: DeepLink.overview) {
                        Text("+\(more) more")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .accessibilityLabel(more == 1 ? "1 more account. Open Headroom." : "\(more) more accounts. Open Headroom.")
                }
            }
            .frame(maxHeight: .infinity, alignment: .center)
        }
    }

    private func row(_ account: WidgetSnapshot.Account) -> some View {
        let colors = ProviderColors(providerId: account.providerId, colorScheme: colorScheme)
        let fraction = snapshot.shown(account.usedPercent) / 100
        return HStack(spacing: 8) {
            Text(account.name)
                .font(.caption)
                .lineLimit(1)
                .frame(width: 70, alignment: .leading)
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(colors.container)
                    Capsule()
                        .fill(account.needsAttention && !account.signInExpired ? .red : colors.accent)
                        .frame(width: proxy.size.width * min(max(fraction, 0), 1))
                    if let expected = account.expectedPercent {
                        Rectangle()
                            .fill(.primary.opacity(0.7))
                            .frame(width: 2)
                            .offset(x: proxy.size.width * snapshot.shown(expected) / 100 - 1)
                    }
                }
            }
            .frame(height: 8)
            Text(WidgetTexts.percent(snapshot.shown(account.usedPercent)))
                .font(.caption.monospacedDigit().bold())
                .frame(width: 38, alignment: .trailing)
            WidgetBadge(account: account)
        }
        .opacity(account.signInExpired ? 0.6 : 1)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(WidgetTexts.gauge(account, left: snapshot.showsLeft))
    }
}
