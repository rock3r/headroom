import HeadroomKit
import SwiftUI

/// The redeem sheet's usage: one bar per window. The windows a reset clears are drawn in full and
/// the others dimmed, so its scope reads at a glance. While the provider works the cleared bars
/// shimmer; when the new usage arrives they refill one after another.
struct RefillBars: View {
    let account: AccountUi
    let cleared: Set<String>
    let working: Bool
    @Environment(\.colorScheme) private var colorScheme
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(spacing: 14) {
            ForEach(windows) { window in
                let isCleared = cleared.contains(window.id)
                HStack(spacing: 10) {
                    Text(window.label)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .frame(width: 92, alignment: .leading)
                    QuotaBar(
                        window: window,
                        color: ProviderColors(providerId: account.providerId, colorScheme: colorScheme).accent,
                        showsLeft: model.showsLeft,
                        showsPace: false,
                        delay: Double(rank(of: window)) * 0.16
                    )
                    .shimmer(working && isCleared)
                    Text(Texts.percent(window, left: model.showsLeft))
                        .font(.caption.monospacedDigit())
                        .contentTransition(.numericText())
                }
                .opacity(isCleared || cleared.isEmpty ? 1 : 0.4)
                .accessibilityElement(children: .combine)
            }
        }
    }

    private var windows: [WindowUi] {
        account.windows.filter { !$0.isUnlimited && !$0.isInformational }
    }

    /// The cleared bars refill in order, a stagger apart; the others keep their place.
    private func rank(of window: WindowUi) -> Int {
        windows.filter { cleared.contains($0.id) }.firstIndex { $0.id == window.id } ?? 0
    }
}
