import HeadroomKit
import SwiftUI

/// The detail's big ring, as on Android: the main window outside, the session inside, and the big
/// number in the middle.
struct HeroRing: View {
    let account: AccountUi
    let session: WindowUi?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(AppModel.self) private var model
    @ScaledMetric(relativeTo: .largeTitle) private var ringSize = 190.0

    // Grow with the type, but leave room for list insets on the narrowest phone.
    private var diameter: Double { min(ringSize, 284) }

    var body: some View {
        if let primary = account.primary {
            VStack(spacing: 12) {
                ZStack {
                    ring(primary, inset: 0, width: 16, color: primaryColor(primary))
                    if let session {
                        ring(session, inset: 24, width: 10, color: colors.accent.opacity(0.6))
                    }
                    VStack(spacing: 0) {
                        Text("\(Int(shown(primary).rounded()))%")
                            .alloFont(.number, size: 44, relativeTo: .largeTitle)
                            .minimumScaleFactor(0.5)
                            .lineLimit(1)
                            .contentTransition(.numericText())
                        Text(Texts.quotaLabel(primary.kind, left: model.showsLeft))
                            .alloFont(.caption)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: diameter - 80)
                }
                .frame(width: diameter, height: diameter)
                if session != nil {
                    HStack(spacing: 16) {
                        legend(Texts.windowKind(primary.kind), color: primaryColor(primary))
                        legend("Session", color: colors.accent.opacity(0.6))
                    }
                    .alloFont(.caption)
                }
            }
            .frame(maxWidth: .infinity)
            .opacity(account.signInExpired ? 0.55 : 1)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Texts.percent(primary, left: model.showsLeft))
        }
    }

    private var colors: ProviderColors {
        ProviderColors(providerId: account.providerId, colorScheme: colorScheme)
    }

    private func primaryColor(_ window: WindowUi) -> Color {
        account.needsAttention && !account.signInExpired ? .red : colors.accent
    }

    private func shown(_ window: WindowUi) -> Double {
        model.showsLeft ? window.leftPercent : window.usedPercent
    }

    private func ring(_ window: WindowUi, inset: Double, width: Double, color: Color) -> some View {
        ZStack {
            Circle()
                .stroke(.quaternary, lineWidth: width)
            Circle()
                .trim(from: 0, to: shown(window) / 100)
                .stroke(color, style: StrokeStyle(lineWidth: width, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .padding(inset + width / 2)
        .animation(.smooth, value: shown(window))
    }

    private func legend(_ title: LocalizedStringKey, color: Color) -> some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(title)
        }
        .foregroundStyle(.secondary)
    }
}
