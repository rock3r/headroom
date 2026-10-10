import HeadroomKit
import SwiftUI

/// The reset that comes next, with a countdown that stays current.
struct NextResetRow: View {
    let next: NextResetUi

    var body: some View {
        TimelineView(.everyMinute) { context in
            let countdown = ViewModelsKt.countdown(
                epochSeconds: next.resetsAtEpochSeconds,
                nowEpochSeconds: Int64(context.date.timeIntervalSince1970)
            )
            VStack(alignment: .leading, spacing: 2) {
                Text("\(next.accountTitle) · \(next.windowLabel)")
                    .font(.headline)
                Group {
                    // The provider only adds something when the account has a name of its own.
                    if next.providerName == next.accountTitle {
                        Text("in \(countdown)")
                    } else {
                        Text("\(next.providerName) · in \(countdown)")
                    }
                }
                .font(.subheadline)
                .foregroundStyle(.secondary)
            }
            .accessibilityElement(children: .combine)
        }
    }
}
