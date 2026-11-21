import HeadroomKit
import SwiftUI

/// How much of each limit was left unused when it reset.
struct LeftOverCard: View {
    let leftOver: LeftOverUi?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(AppModel.self) private var model

    var body: some View {
        StatCard(title: "Unused quota at reset", empty: leftOver == nil ? "No resets recorded yet. Unused quota appears after the first reset." : nil) {
            if let leftOver {
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(Int(leftOver.averageLeft.rounded()))%")
                        .alloFont(.number, size: 34, relativeTo: .largeTitle)
                    Text(leftOver.resets == 1
                         ? "left unused, on average, when a limit reset. From 1 reset."
                         : "left unused, on average, when a limit reset. From \(leftOver.resets) resets.")
                        .foregroundStyle(.secondary)
                }
                ForEach(leftOver.accounts, id: \.account.id) { account in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 8) {
                            ProviderAvatar(provider: model.provider(id: account.account.providerId), size: 24)
                            Text(account.account.name)
                                .fixedSize(horizontal: false, vertical: true)
                            Spacer(minLength: 8)
                            Text("\(Int(account.averageLeft.rounded()))%")
                                .monospacedDigit()
                                .fixedSize()
                        }
                        GeometryReader { proxy in
                            Capsule().fill(.quaternary)
                                .overlay(alignment: .leading) {
                                    Capsule()
                                        .fill(ProviderColors(providerId: account.account.providerId, colorScheme: colorScheme).accent)
                                        .frame(width: proxy.size.width * min(max(account.averageLeft / 100, 0), 1))
                                }
                        }
                        .frame(height: 8)
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(account.account.name), \(Int(account.averageLeft.rounded()))% unused")
                }
            }
        }
    }
}
