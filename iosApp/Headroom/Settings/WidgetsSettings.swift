import SwiftUI

/// The widget styles Headroom offers, and how to add them: iOS lets only the user add a widget.
struct WidgetsSettings: View {
    var body: some View {
        Section {
            style("Rings · small or medium", "Your tightest limit as a ring, or up to four accounts.")
            style("Bars · medium or large", "Every account as a bar, with its pace tick.")
            style("Shape · small or medium", "A shape that changes as the limit fills up. The number always stays.")
            style("Countdown · small", "Counts down to the next weekly reset of your accounts.")
            style("Lock Screen · circular, rectangular or inline", "A gauge for your tightest limit, your accounts, or the next reset.")
        } header: {
            Text("Home screen widgets")
        } footer: {
            Text("Touch and hold your Home Screen or Lock Screen, tap Edit, then Add Widget, and choose Headroom. Touch and hold a widget, then Edit Widget, to choose its accounts.")
        }
    }

    private func style(_ title: LocalizedStringKey, _ body: LocalizedStringKey) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
            Text(body)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }
}
