import HeadroomKit
import SwiftUI

/// The closest call next to the biggest day, as the Android highlights row.
struct HighlightsRow: View {
    let closestCall: PastResetUi?
    let allHits: Bool
    let biggestDay: BiggestDayUi?

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .top, spacing: 16) {
                closest
                Divider()
                biggest
            }
            VStack(alignment: .leading, spacing: 16) {
                closest
                Divider()
                biggest
            }
        }
        .padding(.vertical, 4)
    }

    private var closest: some View {
        Highlight(title: "Closest call", account: closestCall?.account,
                  value: closestCall.map { Int($0.peak.rounded()) },
                  sentence: closestCall.map { call in
                      let date = Date(epochSeconds: call.peakAtEpochSeconds).formatted(.dateTime.month().day())
                      return String(localized: "\(call.account.name) reached \(Int(call.peak.rounded()))% on \(date), then the limit reset.")
                  },
                  empty: allHits
                      ? "Every recorded reset hit the limit. No close calls, only direct hits."
                      : "Appears once a limit resets.")
    }

    private var biggest: some View {
        Highlight(title: "Biggest day", account: biggestDay?.account,
                  value: biggestDay.map { Int($0.points.rounded()) },
                  sentence: biggestDay.map { day in
                      String(localized: "\(day.account.name) used \(Int(day.points.rounded()))% of its limit in one day, on \(BiggestDayText.day(day.date)).")
                  },
                  empty: "No busy days yet. This fills in after a few syncs.")
    }
}

/// One highlight: its title, the big number with the account's avatar, and its sentence.
private struct Highlight: View {
    let title: LocalizedStringKey
    let account: StatAccountUi?
    let value: Int?
    let sentence: String?
    let empty: LocalizedStringKey
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.headline)
            if let value, let sentence, let account {
                HStack(spacing: 8) {
                    ProviderAvatar(provider: model.provider(id: account.providerId), size: 28)
                    Text("\(value)%")
                        .font(.title.bold().monospacedDigit())
                }
                Text(sentence)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            } else {
                Text(empty)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// "Sep 27", from HeadroomKit's `yyyy-MM-dd`.
enum BiggestDayText {
    static func day(_ iso: String) -> String {
        guard let date = try? Date(iso, strategy: .iso8601.year().month().day()) else { return iso }
        return date.formatted(.dateTime.month().day())
    }
}
