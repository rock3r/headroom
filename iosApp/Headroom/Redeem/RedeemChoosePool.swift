import HeadroomKit
import SwiftUI

/// An account with resets for different limits: which one to use.
struct RedeemChoosePool: View {
    let pools: [ResetPoolUi]
    let onChoose: (ResetPoolUi) -> Void

    var body: some View {
        List {
            Section("Which limit do you want to reset?") {
                ForEach(pools) { pool in
                    Button {
                        onChoose(pool)
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(pool.label) · ^[\(pool.available) available](inflect: true)")
                            Text(RedeemTexts.scope(pool))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .disabled(!pool.canUseNow)
                }
            }
        }
    }
}
