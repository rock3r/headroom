import SwiftUI

/// A ring of the share used or left, in the provider's colour, with an optional inner ring for the
/// session. A ring that needs attention turns red and wavy, as the Android big ring.
struct RingGauge: View {
    let account: WidgetSnapshot.Account
    let snapshot: WidgetSnapshot
    var lineWidth = 8.0
    var showsSession = true
    var showsNumber = true
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let colors = ProviderColors(providerId: account.providerId, colorScheme: colorScheme)
        ZStack {
            Circle().stroke(colors.container, lineWidth: lineWidth)
            if account.needsAttention && !account.signInExpired {
                WavyArc(fraction: snapshot.shown(account.usedPercent) / 100)
                    .stroke(.red, style: StrokeStyle(lineWidth: lineWidth * 0.75, lineCap: .round))
            } else {
                Circle()
                    .trim(from: 0, to: snapshot.shown(account.usedPercent) / 100)
                    .stroke(colors.accent, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                    .rotationEffect(.degrees(-90))
            }
            if showsSession, let session = account.sessionUsedPercent {
                Group {
                    Circle().stroke(colors.container, lineWidth: lineWidth * 0.6)
                    Circle()
                        .trim(from: 0, to: snapshot.shown(session) / 100)
                        .stroke(colors.accent.opacity(0.6), style: StrokeStyle(lineWidth: lineWidth * 0.6, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                }
                .padding(lineWidth * 1.4)
            }
            if showsNumber {
                Text(WidgetTexts.percent(snapshot.shown(account.usedPercent)))
                    .font(.system(.headline, design: .rounded).monospacedDigit())
                    .minimumScaleFactor(0.5)
                    .padding(lineWidth * (account.sessionUsedPercent == nil ? 1.2 : 2.4))
            }
        }
        .padding(lineWidth / 2)
        .opacity(account.signInExpired ? 0.5 : 1)
        .overlay(alignment: .topTrailing) { WidgetBadge(account: account) }
    }
}

/// A ring's trimmed arc as a wave, for a limit that needs attention.
struct WavyArc: Shape {
    let fraction: Double

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let radius = min(rect.width, rect.height) / 2
        let steps = max(Int(fraction * 240), 2)
        for step in 0 ... steps {
            let t = Double(step) / Double(steps) * fraction
            let angle = t * 2 * .pi - .pi / 2
            let r = radius + sin(t * 2 * .pi * 14) * radius * 0.05
            let point = CGPoint(x: center.x + cos(angle) * r, y: center.y + sin(angle) * r)
            if step == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        return path
    }
}

/// A count of the resets available now, or "!" when the sign-in expired.
struct WidgetBadge: View {
    let account: WidgetSnapshot.Account

    var body: some View {
        if account.signInExpired {
            Text("!")
                .font(.caption2.bold())
                .frame(width: 16, height: 16)
                .background(.red, in: .circle)
                .foregroundStyle(.white)
        } else if account.resetsAvailable > 0 {
            Text("\(account.resetsAvailable)")
                .font(.caption2.bold().monospacedDigit())
                .padding(.horizontal, 4)
                .frame(minWidth: 16, minHeight: 16)
                .background(.tint, in: .capsule)
                .foregroundStyle(.white)
        }
    }
}
