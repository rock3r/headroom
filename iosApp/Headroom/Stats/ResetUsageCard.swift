import HeadroomKit
import SwiftUI

/// How the usage-limit resets were spent over a period: used in Headroom or elsewhere, or expired.
struct ResetUsageCard: View {
    let periods: [ResetUsageUi]
    @State private var period = "fourWeeks"

    var body: some View {
        Section {
            Picker("Period", selection: $period) {
                ForEach(periods, id: \.period) { usage in
                    Text(StatsTexts.period(usage.period)).tag(usage.period)
                }
            }
            .pickerStyle(.segmented)
            if let usage = periods.first(where: { $0.period == period }) {
                if usage.used == 0 && usage.expired == 0 {
                    Text("No resets were used or expired in this period.")
                        .foregroundStyle(.secondary)
                } else {
                    HStack(alignment: .firstTextBaseline, spacing: 24) {
                        VStack(alignment: .leading) {
                            Text("\(usage.used)")
                                .font(.largeTitle.bold().monospacedDigit())
                            Text("used")
                                .foregroundStyle(.secondary)
                        }
                        VStack(alignment: .leading) {
                            Text("\(usage.expired)")
                                .font(.largeTitle.bold().monospacedDigit())
                            Text("expired unused")
                                .foregroundStyle(.secondary)
                        }
                    }
                    Text("\(usage.usedInHeadroom) in Headroom, \(usage.usedElsewhere) elsewhere.")
                    if let givenBack = Self.givenBack(usage.givenBack) {
                        Text(givenBack)
                    }
                    ForEach(usage.providers, id: \.providerId) { provider in
                        LabeledContent(provider.providerName) {
                            Text("\(provider.used) used, \(provider.expired) expired unused")
                        }
                    }
                }
            }
        } header: {
            Text("Usage limit resets")
        } footer: {
            Text("A reset counts as used when it is gone before its expiry date. \"Gave back\" adds up how much of each limit was used when a reset cleared it. \"About\" means part of it comes from usage synced some time before the reset.")
        }
    }

    /// "Gave back 1.5 times the weekly limit.", or nil when nothing was given back.
    static func givenBack(_ givenBack: GivenBackUi) -> String? {
        guard !givenBack.limits.isEmpty else { return nil }
        let limits = givenBack.limits
            .map { StatsTexts.givenBack(kind: $0.kind, times: $0.times) }
            .formatted(.list(type: .and))
        return givenBack.estimated
            ? String(localized: "Gave back about \(limits).")
            : String(localized: "Gave back \(limits).")
    }
}
