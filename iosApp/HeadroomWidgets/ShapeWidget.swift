import SwiftUI
import WidgetKit

/// A shape that gets busier as a limit fills up, with the number inside.
struct ShapeWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "dev.sebastiano.headroom.shape", intent: AccountsConfiguration.self,
                               provider: SnapshotProvider()) { entry in
            ShapeView(snapshot: entry.snapshot)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Shape")
        .description("A shape that gets busier as a limit fills up, with the number inside.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct ShapeView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family

    var body: some View {
        let accounts = Array(snapshot.accounts.prefix(family == .systemMedium ? 4 : 1))
        if snapshot.isDemo || accounts.isEmpty {
            WidgetEmpty(snapshot: snapshot)
        } else if accounts.count == 1, let account = accounts.first {
            cell(account, big: true)
                .widgetURL(WidgetTexts.link(account))
        } else {
            HStack(spacing: 10) {
                ForEach(accounts) { account in
                    Link(destination: WidgetTexts.link(account)) {
                        cell(account, big: false)
                    }
                }
            }
        }
    }

    private func cell(_ account: WidgetSnapshot.Account, big: Bool) -> some View {
        VStack(spacing: 4) {
            UsageShapeView(account: account, snapshot: snapshot, big: big)
            Text(account.name)
                .font(.caption2)
                .lineLimit(1)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(WidgetTexts.gauge(account, left: snapshot.showsLeft))
    }
}

/// The shape for one account: calm while there is room, a flower from 70% used, a clover from 85%.
struct UsageShapeView: View {
    let account: WidgetSnapshot.Account
    let snapshot: WidgetSnapshot
    let big: Bool
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let colors = ProviderColors(providerId: account.providerId, colorScheme: colorScheme)
        PolarShape.forPercent(account.usedPercent)
            .fill(account.needsAttention && !account.signInExpired ? Color.red.opacity(0.85) : colors.accent)
            .aspectRatio(1, contentMode: .fit)
            .overlay {
                Text(WidgetTexts.percent(snapshot.shown(account.usedPercent)))
                    .font(.system(big ? .title : .headline, design: .rounded).bold().monospacedDigit())
                    .foregroundStyle(.white)
                    .minimumScaleFactor(0.5)
            }
            .opacity(account.signInExpired ? 0.5 : 1)
            .overlay(alignment: .topTrailing) { WidgetBadge(account: account) }
    }
}

/// Soft Material-like shapes as polar curves, as the Android widget draws them:
/// `r(t) = (1 + depth * cos(lobes * t)) / (1 + depth)`.
struct PolarShape: Shape {
    let lobes: Int
    let depth: Double

    static func forPercent(_ used: Double) -> PolarShape {
        switch used {
        case 85...: PolarShape(lobes: 4, depth: 0.2)
        case 70...: PolarShape(lobes: 8, depth: 0.12)
        default: PolarShape(lobes: 9, depth: 0.075)
        }
    }

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let radius = min(rect.width, rect.height) / 2
        let steps = 96
        for step in 0 ..< steps {
            let theta = Double(step) / Double(steps) * 2 * .pi
            let r = (1 + depth * cos(Double(lobes) * theta)) / (1 + depth) * radius
            let point = CGPoint(x: center.x + r * sin(theta), y: center.y - r * cos(theta))
            if step == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }
}
