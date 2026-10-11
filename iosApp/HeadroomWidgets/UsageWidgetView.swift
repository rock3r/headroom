import SwiftUI
import WidgetKit

struct UsageWidgetView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family

    var body: some View {
        if snapshot.isDemo || snapshot.accounts.isEmpty {
            NoAccountsView()
        } else {
            switch family {
            case .accessoryCircular:
                if let account = snapshot.tightest {
                    Gauge(value: snapshot.shown(account), in: 0 ... 100) {
                        Text(account.title.prefix(1))
                    } currentValueLabel: {
                        Text("\(Int(snapshot.shown(account).rounded()))")
                    }
                    .gaugeStyle(.accessoryCircularCapacity)
                    .widgetLabel(account.title)
                }
            case .accessoryRectangular:
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(snapshot.accounts.prefix(3)) { account in
                        HStack {
                            Text(account.title)
                            Spacer()
                            Text(PercentText.short(snapshot.shown(account), left: snapshot.showsLeft))
                                .monospacedDigit()
                        }
                        .font(.caption)
                    }
                }
            case .accessoryInline:
                if let tile = snapshot.tightestTile {
                    Text("\(tile.name) · \(tile.value)")
                }
            case .systemMedium:
                HStack(spacing: 12) {
                    ForEach(snapshot.accounts.prefix(4)) { account in
                        AccountRing(account: account, snapshot: snapshot)
                    }
                }
            default:
                if let account = snapshot.tightest {
                    VStack(spacing: 8) {
                        AccountRing(account: account, snapshot: snapshot)
                        if let resetsAt = account.resetsAt {
                            Text("Resets \(resetsAt, style: .relative)")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                        }
                    }
                }
            }
        }
    }
}
