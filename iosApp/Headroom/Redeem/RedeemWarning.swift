import SwiftUI

/// A line the user must not miss before confirming, on a tinted panel with an icon.
struct RedeemWarning: View {
    let text: LocalizedStringKey

    var body: some View {
        Label(text, systemImage: "exclamationmark.circle")
            .font(.subheadline)
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.orange.opacity(0.14), in: .rect(cornerRadius: 14))
    }
}
