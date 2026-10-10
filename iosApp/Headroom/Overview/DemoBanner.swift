import SwiftUI

/// Shown while no account is signed in and the overview shows example numbers.
struct DemoBanner: View {
    let onAdd: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Demo data", systemImage: "sparkles")
                .font(.headline)
            Text("These numbers are examples. Add an account to see your own limits.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button("Add account", action: onAdd)
                .buttonStyle(.borderedProminent)
        }
        .padding(.vertical, 4)
    }
}
