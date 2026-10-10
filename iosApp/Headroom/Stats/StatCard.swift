import SwiftUI

/// A stat's section: its title, and its content or why it is empty.
struct StatCard<Content: View>: View {
    let title: LocalizedStringKey
    let empty: LocalizedStringKey?
    @ViewBuilder let content: Content

    var body: some View {
        Section(title) {
            if let empty {
                Text(empty)
                    .foregroundStyle(.secondary)
            } else {
                content
            }
        }
    }
}
