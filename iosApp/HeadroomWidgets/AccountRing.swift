import SwiftUI

/// An account's main limit as a ring in its provider's colour, with its logo inside.
struct AccountRing: View {
    let account: WidgetSnapshot.Account
    let snapshot: WidgetSnapshot
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let colors = ProviderColors(providerId: account.providerId, colorScheme: colorScheme)
        VStack(spacing: 6) {
            ZStack {
                Circle()
                    .stroke(colors.container, lineWidth: 7)
                Circle()
                    .trim(from: 0, to: snapshot.shown(account) / 100)
                    .stroke(account.needsAttention ? .red : colors.accent, style: StrokeStyle(lineWidth: 7, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                if let logo = account.logo {
                    LogoShape(pathData: logo.pathData, viewport: logo.viewport, inset: logo.inset)
                        .fill(colors.onContainer)
                        .padding(16)
                }
            }
            .opacity(account.signInExpired ? 0.5 : 1)
            Text(account.title)
                .font(.caption2)
                .lineLimit(1)
            Text(PercentText.short(snapshot.shown(account), left: snapshot.showsLeft))
                .font(.caption.bold().monospacedDigit())
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(account.title), \(PercentText.short(snapshot.shown(account), left: snapshot.showsLeft))")
    }
}
