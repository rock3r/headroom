import HeadroomKit
import SwiftUI

/// The delights, each with a button to try it, and the reset Live Activity.
struct DelightsSettings: View {
    let settings: SettingsUi
    @Environment(AppModel.self) private var model

    var body: some View {
        Section {
            HStack {
                toggle("refreshShimmer", "Refresh shimmer", settings.refreshShimmer,
                       body: "A soft sheen sweeps across the screen when new numbers arrive.")
                Button("Try", action: model.tryShimmer)
                    .buttonStyle(.bordered)
                    .accessibilityLabel("Try the refresh shimmer")
                    .disabled(reduced)
            }
            HStack {
                toggle("resetConfetti", "Reset confetti", settings.resetConfetti,
                       body: "Confetti bursts from an account when its limit resets while the app is open.")
                Button("Try", action: model.tryConfetti)
                    .buttonStyle(.bordered)
                    .accessibilityLabel("Try the reset confetti")
                    .disabled(reduced)
            }
            toggle("resetIsland", "Reset Live Activity", settings.resetIsland,
                   body: "When a limit resets within a few hours, its countdown shows on the Lock Screen and in the Dynamic Island.")
        } header: {
            Text("Delights")
        } footer: {
            Text("Neither plays while motion is reduced.")
        }
    }

    private var reduced: Bool {
        DelightCenter.motionReduced(settings.reduceMotion)
    }

    private func toggle(_ id: String, _ title: LocalizedStringKey, _ isOn: Bool, body: LocalizedStringKey) -> some View {
        Toggle(isOn: Binding(get: { isOn }, set: { model.headroom.settings.setSwitch(id: id, enabled: $0) })) {
            Text(title)
            Text(body)
        }
    }
}
