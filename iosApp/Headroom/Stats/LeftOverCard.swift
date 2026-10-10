import HeadroomKit
import SwiftUI

/// How much of each limit was left unused when it reset.
struct LeftOverCard: View {
    let leftOver: LeftOverUi?

    var body: some View {
        StatCard(title: "Left on the table", empty: leftOver == nil ? "No resets yet, so nothing was left on the table." : nil) {
            if let leftOver {
                HStack(alignment: .firstTextBaseline) {
                    Text("\(Int(leftOver.averageLeft.rounded()))%")
                        .font(.largeTitle.bold().monospacedDigit())
                    Text("left unused, on average, when a limit reset. From ^[\(leftOver.resets) reset](inflect: true).")
                        .foregroundStyle(.secondary)
                }
                ForEach(leftOver.accounts, id: \.account.id) { account in
                    HStack {
                        Text(account.account.name)
                        Spacer()
                        Text("\(Int(account.averageLeft.rounded()))%")
                            .monospacedDigit()
                    }
                    .accessibilityElement(children: .combine)
                }
            }
        }
    }
}
