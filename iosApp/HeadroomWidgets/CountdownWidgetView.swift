import SwiftUI
import WidgetKit

struct CountdownWidgetView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family

    var body: some View {
        if let next = snapshot.nextReset, !snapshot.isDemo {
            switch family {
            case .accessoryInline:
                Text("\(next.accountTitle) · \(next.resetsAt, style: .relative)")
            case .accessoryRectangular:
                VStack(alignment: .leading) {
                    Text("Next reset")
                        .font(.caption2)
                    Text("\(next.accountTitle) · \(next.windowLabel)")
                        .font(.headline)
                    Text(next.resetsAt, style: .relative)
                        .font(.caption.monospacedDigit())
                }
            default:
                VStack(alignment: .leading, spacing: 4) {
                    Text("Next reset")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text(next.accountTitle)
                        .font(.headline)
                    Text(next.windowLabel)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(next.resetsAt, style: .relative)
                        .font(.title3.bold().monospacedDigit())
                    Text(next.resetsAt, format: .dateTime.weekday().hour().minute())
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        } else {
            NoAccountsView()
        }
    }
}
