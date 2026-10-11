import SwiftUI

/// The provider is working on it.
struct RedeemBusy: View {
    let text: LocalizedStringKey

    var body: some View {
        ProgressView {
            Text(text)
        }
        .frame(maxWidth: .infinity, minHeight: 120)
    }
}
