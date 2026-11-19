import SwiftUI

/// Shown while no account is signed in and the overview shows example numbers.
struct DemoBanner: View {
    var body: some View {
        Label {
            Text("**Demo data** · Add an account with +")
        } icon: {
            Image(systemName: "sparkles")
        }
        .alloFont(.footnote)
        .foregroundStyle(Color.accentColor)
        .accessibilityIdentifier("demo-data-banner")
        .listRowBackground(Color.accentColor.opacity(0.12))
    }
}
