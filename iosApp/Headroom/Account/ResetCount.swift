import HeadroomKit
import SwiftUI

/// "2 (+5)": the resets available now and those queued behind them. A tap explains the count.
struct ResetCount: View {
    let resets: AccountResetsUi
    @State private var explaining = false

    var body: some View {
        Button {
            explaining = true
        } label: {
            Group {
                if resets.queued > 0 {
                    Text("\(resets.availableNow) (+\(resets.queued))")
                } else {
                    Text("\(resets.availableNow)")
                }
            }
            .font(.title2.bold().monospacedDigit())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(description)
        .accessibilityHint("Explain the count")
        .popover(isPresented: $explaining) {
            Text(description)
                .padding()
                .frame(idealWidth: 280)
                .presentationCompactAdaptation(.popover)
        }
    }

    private var description: String {
        let now = Formats.inflected("^[\(resets.availableNow) reset](inflect: true) available now")
        guard resets.queued > 0 else { return now }
        let queued = resets.queued == 1
            ? String(localized: "1 more is queued: it should unlock after these are used.")
            : String(localized: "\(resets.queued) more are queued: they should unlock after these are used.")
        return "\(now). \(queued)"
    }
}
