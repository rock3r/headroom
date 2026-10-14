import HeadroomKit
import SwiftUI

/// An account with resets for different limits: which one to use.
struct RedeemChoosePool: View {
    let pools: [ResetPoolUi]
    let onChoose: (ResetPoolUi) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Which limit do you want to reset?")
                .font(.title2.bold())
            ForEach(pools) { pool in
                Button {
                    onChoose(pool)
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(pool.label) · ^[\(pool.available) available](inflect: true)")
                            .font(.headline)
                        Text(RedeemTexts.scope(pool))
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                    .background(.fill.tertiary, in: .rect(cornerRadius: 16))
                }
                .buttonStyle(.plain)
            }
        }
    }
}
