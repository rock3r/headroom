import HeadroomKit
import SwiftUI

/// "Resets in 15h 28m", kept current every minute, or the date a credit expires.
struct ResetLabel: View {
    let window: WindowUi

    var body: some View {
        TimelineView(.everyMinute) { context in
            if let resetsAt = window.resetsAtEpochSeconds?.int64Value {
                let countdown = ViewModelsKt.countdown(
                    epochSeconds: resetsAt,
                    nowEpochSeconds: Int64(context.date.timeIntervalSince1970)
                )
                Text("Resets in \(countdown)")
            } else if let expiresAt = window.expiresAtEpochSeconds?.int64Value {
                Text("Expires \(Date(timeIntervalSince1970: TimeInterval(expiresAt)), format: .dateTime.day().month())")
            }
        }
    }
}
