import HeadroomKit
import SwiftUI

/// The providers an account can be added for.
struct ProviderPicker: View {
    let providers: [ProviderUi]
    let onPick: (ProviderUi) -> Void

    var body: some View {
        List {
            Section("Choose a provider") {
                ForEach(providers) { provider in
                    Button {
                        onPick(provider)
                    } label: {
                        Label {
                            Text(provider.name)
                                .foregroundStyle(.primary)
                        } icon: {
                            ProviderAvatar(provider: provider, size: 32)
                        }
                    }
                }
            }
        }
    }
}
