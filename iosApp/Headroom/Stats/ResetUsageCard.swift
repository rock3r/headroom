import HeadroomKit
import SwiftUI

/// How the usage-limit resets were spent over a period: used in Headroom or elsewhere, or expired.
struct ResetUsageCard: View {
    let periods: [ResetUsageUi]
    @State private var period = "fourWeeks"
    @Environment(AppModel.self) private var model

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
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .firstTextBaseline, spacing: 24) {
                            number(usage.used, "used")
                            number(usage.expired, "expired unused")
                        }
                        Text("\(usage.usedInHeadroom) in Headroom, \(usage.usedElsewhere) elsewhere.")
                            .foregroundStyle(.secondary)
                        if let givenBack = Self.givenBack(usage.givenBack) {
                            Text(givenBack)
                        }
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(description(usage))
                    ForEach(usage.providers, id: \.providerId) { provider in
                        HStack(alignment: .top, spacing: 10) {
                            ProviderAvatar(provider: model.provider(id: provider.providerId), size: 24)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(provider.providerName)
                                Text("\(provider.used) used, \(provider.expired) expired")
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                                if let givenBack = Self.givenBack(provider.givenBack) {
                                    Text(givenBack)
                                        .font(.footnote)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        .accessibilityElement(children: .combine)
                    }
                }
            }
        } header: {
            Text("Usage limit resets")
        } footer: {
            Text("A reset counts as used when it is gone before its expiry date. \"Gave back\" adds up how much of each limit was used when a reset cleared it. \"About\" means part of it comes from usage synced some time before the reset.")
        }
    }

    private func number(_ value: Int32, _ label: LocalizedStringKey) -> some View {
        VStack(alignment: .leading) {
            Text("\(value)")
                .font(.largeTitle.bold().monospacedDigit())
            Text(label)
                .foregroundStyle(.secondary)
        }
    }

    private func description(_ usage: ResetUsageUi) -> String {
        let period = String(localized: StatsTexts.periodResource(usage.period))
        return String(localized: "Usage limit resets in the last \(period): \(usage.used) used, \(usage.expired) expired unused.")
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
