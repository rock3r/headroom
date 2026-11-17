import SwiftUI

/// Shown while no account is signed in and the overview shows example numbers.
struct DemoBanner: View {
    let onAdd: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Demo data", systemImage: "sparkles")
                .alloFont(.heading, size: 17, relativeTo: .headline)
            Text("These numbers are examples. Add an account to see your own limits.")
                .alloFont(.subheadline)
                .foregroundStyle(.secondary)
            Button("Add account", action: onAdd)
                .buttonStyle(.borderedProminent)
        }
        .padding(.vertical, 4)
    }
}
