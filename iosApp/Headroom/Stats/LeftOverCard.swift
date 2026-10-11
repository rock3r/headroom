import HeadroomKit
import SwiftUI

/// How much of each limit was left unused when it reset.
struct LeftOverCard: View {
    let leftOver: LeftOverUi?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(AppModel.self) private var model

    var body: some View {
        StatCard(title: "Left on the table", empty: leftOver == nil ? "No resets yet, so nothing was left on the table." : nil) {
            if let leftOver {
                HStack(alignment: .firstTextBaseline) {
                    Text("\(Int(leftOver.averageLeft.rounded()))%")
                        .font(.largeTitle.bold().monospacedDigit())
                    Text(leftOver.resets == 1
                         ? "left unused, on average, when a limit reset. From 1 reset."
                         : "left unused, on average, when a limit reset. From \(leftOver.resets) resets.")
                        .foregroundStyle(.secondary)
                }
                ForEach(leftOver.accounts, id: \.account.id) { account in
                    HStack(spacing: 10) {
                        ProviderAvatar(provider: model.provider(id: account.account.providerId), size: 24)
                        Text(account.account.name)
                            .frame(width: 100, alignment: .leading)
                            .lineLimit(1)
                        GeometryReader { proxy in
                            Capsule().fill(.quaternary)
                                .overlay(alignment: .leading) {
                                    Capsule()
                                        .fill(ProviderColors(providerId: account.account.providerId, colorScheme: colorScheme).accent)
                                        .frame(width: proxy.size.width * min(max(account.averageLeft / 100, 0), 1))
                                }
                        }
                        .frame(height: 8)
                        Text("\(Int(account.averageLeft.rounded()))%")
                            .monospacedDigit()
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(description(leftOver))
            }
        }
    }

    private func description(_ leftOver: LeftOverUi) -> String {
        let accounts = leftOver.accounts.map { "\($0.account.name) \(Int($0.averageLeft.rounded()))%" }
            .formatted(.list(type: .and))
        return String(localized: "On average, \(Int(leftOver.averageLeft.rounded()))% of each limit was left unused at reset. \(accounts).")
    }
}
