import HeadroomKit
import SwiftUI

/// The reset that came closest to the limit without hitting it.
struct ClosestCallCard: View {
    let closestCall: PastResetUi?
    let allHits: Bool

    var body: some View {
        StatCard(title: "Closest call", empty: empty) {
            if let call = closestCall {
                let date = Date(epochSeconds: call.peakAtEpochSeconds).formatted(.dateTime.month().day())
                Text("\(call.account.name) reached \(Int(call.peak.rounded()))% on \(date), then the limit reset.")
            }
        }
    }

    private var empty: LocalizedStringKey? {
        if closestCall != nil { return nil }
        return allHits
            ? "Every recorded reset hit the limit. No close calls, only direct hits."
            : "Appears once a limit resets."
    }
}
