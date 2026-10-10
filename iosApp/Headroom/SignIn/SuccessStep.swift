import SwiftUI

struct SuccessStep: View {
    let accountLabel: String
    let onDone: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label("Signed in as \(accountLabel)", systemImage: "checkmark.circle.fill")
        } actions: {
            Button("Done", action: onDone)
                .buttonStyle(.borderedProminent)
        }
    }
}
