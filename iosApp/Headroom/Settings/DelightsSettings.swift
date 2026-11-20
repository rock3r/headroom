import HeadroomKit
import SwiftUI

/// The delights, each with a button to try it, and the reset Live Activity.
struct DelightsSettings: View {
    let settings: SettingsUi
    @Environment(AppModel.self) private var model

    var body: some View {
        Section {
            row("refreshShimmer", "Refresh shimmer", settings.refreshShimmer,
                description: "A soft sheen sweeps across the screen when new numbers arrive.") {
                Button("Try", action: model.tryShimmer)
                    .buttonStyle(.bordered)
                    .accessibilityLabel("Try the refresh shimmer")
                    .disabled(reduced)
            }
            row("resetConfetti", "Reset confetti", settings.resetConfetti,
                description: "Confetti bursts from an account when its limit resets while the app is open.") {
                Button("Try", action: model.tryConfetti)
                    .buttonStyle(.bordered)
                    .accessibilityLabel("Try the reset confetti")
                    .delightAnchor(DelightAnchors.tryConfetti)
                    .disabled(reduced)
            }
            row("resetIsland", "Reset Live Activity", settings.resetIsland,
                description: "When a limit resets within a few hours, its countdown shows on the Lock Screen and in the Dynamic Island.") {
                EmptyView()
            }
        } header: {
            Text("Delights")
        } footer: {
            Text("Refresh shimmer and reset confetti do not play while motion is reduced.")
                .alloFont(.footnote)
        }
    }

    private var reduced: Bool {
        DelightCenter.motionReduced(settings.reduceMotion)
    }

    private func row<Preview: View>(_ id: String, _ title: LocalizedStringKey, _ isOn: Bool,
                                    description: LocalizedStringKey, @ViewBuilder preview: () -> Preview) -> some View {
        HStack(alignment: .top, spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                Text(description)
                    .alloFont(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityHidden(true)
            VStack(alignment: .trailing, spacing: 8) {
                Toggle(isOn: Binding(get: { isOn }, set: { model.headroom.settings.setSwitch(id: id, enabled: $0) })) {
                    Text(title)
                }
                .labelsHidden()
                .accessibilityLabel(Text(title))
                .accessibilityHint(Text(description))
                preview()
            }
            .fixedSize(horizontal: true, vertical: false)
        }
    }
}
