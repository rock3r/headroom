import HeadroomKit
import SwiftUI

/// A window's bar, the share left, its pace and the reset countdown. A window of an account whose
/// sign-in expired is faded, as on Android: its numbers are from the last good sync.
struct WindowSummary: View {
    let window: WindowUi
    let providerId: String
    var stale = false
    @Environment(\.colorScheme) private var colorScheme
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(window.label)
                    .font(.subheadline)
                Spacer()
                Group {
                    if model.showsLeft {
                        Text("\(Int(window.leftPercent.rounded()))% left")
                    } else {
                        Text("\(Int(window.usedPercent.rounded()))% used")
                    }
                }
                .font(.subheadline.monospacedDigit())
                .bold()
                .foregroundStyle(window.needsAttention ? .red : .primary)
            }
            QuotaBar(
                window: window,
                color: ProviderColors(providerId: providerId, colorScheme: colorScheme).accent
            )
            HStack {
                if let pace = window.pace {
                    Text(Texts.pace(pace))
                        .foregroundStyle(pace == "over" ? .red : .secondary)
                }
                Spacer()
                ResetLabel(window: window)
            }
            .font(.footnote)
            .foregroundStyle(.secondary)
        }
        .opacity(stale ? 0.55 : 1)
        .saturation(stale ? 0.2 : 1)
    }
}
