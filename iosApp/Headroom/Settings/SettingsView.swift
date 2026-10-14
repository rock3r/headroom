import HeadroomKit
import SwiftUI

/// Settings, as on Android: accounts, display, appearance, resets and background updates.
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if let settings = model.settings {
                    SettingsForm(settings: settings)
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", action: close)
                }
            }
        }
        // The "Try" previews play here: the overview's own layers sit behind this sheet.
        .overlayPreferenceValue(DelightAnchors.self) { anchors in
            ConfettiLayer(anchors: anchors, previews: true)
        }
        .overlay {
            RefreshShimmer(trigger: model.delights.previewShimmers)
                .ignoresSafeArea()
        }
    }

    private func close() {
        dismiss()
    }
}
