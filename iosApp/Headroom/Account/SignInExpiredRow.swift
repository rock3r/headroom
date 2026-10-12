import SwiftUI

/// The compact call to action on a card whose sign-in expired: when its numbers are from.
struct SignInExpiredRow: View {
    let dataFrom: Date?
    let onSignIn: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Sign-in expired")
                    .font(.subheadline.bold())
                    .foregroundStyle(.red)
                Group {
                    if let dataFrom {
                        Text("Last updated \(Formats.age(dataFrom))")
                    } else {
                        Text("Not updated yet")
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(dataFrom.map { "Sign-in expired, data from \(Formats.age($0))" } ?? "Sign-in expired, no data yet")
            Spacer()
            Button("Sign in", action: onSignIn)
                .buttonStyle(.borderedProminent)
                .tint(.red)
        }
    }
}
