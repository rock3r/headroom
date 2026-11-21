import HeadroomKit
import SwiftUI

/// The detail screen's usage history: every weekly and monthly limit on one chart, each one
/// toggled on or off, with range presets that slide the chart through the history.
struct HistoryChartSection: View {
    let account: AccountUi
    @Environment(AppModel.self) private var model
    @State private var history = HistoryChartModel()
    @State private var domain = Date.now ... Date.now.addingTimeInterval(7 * 86_400)
    /// The preset the chart sits on, or nil once the user drags it away.
    @State private var placedRange: String?
    /// The chart has been placed on its first range: later charts keep the user's place.
    @State private var hasPlaced = false

    var body: some View {
        Group {
            if let chart = history.chart {
                section(chart)
            } else if !history.loaded {
                Section("Usage history") {
                    ProgressView()
                        .frame(maxWidth: .infinity, minHeight: 196)
                }
            }
        }
        // A List drops rows that scroll away, so the watch restarts whenever the section reappears.
        .onAppear { history.show(model.headroom, accountId: account.id) }
        .onChange(of: account.id) { _, id in history.show(model.headroom, accountId: id) }
        .onChange(of: history.chart, initial: true) { _, chart in
            guard chart != nil else {
                hasPlaced = false
                return
            }
            // The first chart opens on the current period. A newer one keeps the preset the chart
            // sits on, so a new period moves it along, and leaves a chart the user dragged alone.
            if !hasPlaced {
                hasPlaced = true
                place(HistoryRanges.shared.PERIOD, animated: false)
            } else if let placedRange {
                place(placedRange, animated: false)
            }
        }
        .onDisappear(perform: history.stop)
    }

    private func section(_ chart: HistoryChartUi) -> some View {
        Section {
            VStack(alignment: .leading, spacing: 10) {
                if chart.windows.count > 1 {
                    limitChips(chart)
                }
                if chart.ranges.count > 1 {
                    Picker("Range", selection: rangeBinding) {
                        ForEach(chart.ranges, id: \.self) { range in
                            Text(Self.rangeLabel(range)).tag(Optional(range))
                        }
                    }
                    .pickerStyle(.segmented)
                }
                HistoryChart(
                    chart: chart,
                    shown: history.shownWindows,
                    lead: history.leadWindow,
                    series: history.series,
                    showsLeft: model.showsLeft,
                    providerId: account.providerId,
                    domain: $domain,
                    onPan: { placedRange = nil }
                )
                .frame(height: 196)
                .animation(.smooth(duration: 0.35), value: history.shownIds)

                if let lead = history.leadWindow {
                    ProjectionText(lead: lead, showsLeft: model.showsLeft)
                }
            }
        } header: {
            HStack {
                Text("Usage history")
                Spacer()
                Text("dashed = even pace")
                    .textCase(nil)
            }
        }
    }

    private func limitChips(_ chart: HistoryChartUi) -> some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                ForEach(Array(chart.windows.enumerated()), id: \.element.id) { slot, window in
                    LimitChip(
                        label: PaceTexts.chartWindowLabel(window.label, providerId: account.providerId),
                        slot: slot,
                        providerId: account.providerId,
                        isOn: history.shownIds.contains(window.id)
                    ) {
                        withAnimation(.smooth(duration: 0.35)) {
                            let shown = history.shownIds
                            history.toggle(window.id)
                            // The period follows the shortest limit shown, so it moves with them.
                            if history.shownIds != shown, placedRange == HistoryRanges.shared.PERIOD {
                                place(HistoryRanges.shared.PERIOD, animated: false)
                            }
                        }
                    }
                }
            }
        }
        .scrollIndicators(.hidden)
        .scrollClipDisabled()
    }

    /// The preset the chart sits on, or none once the user scrolls away from it.
    private var rangeBinding: Binding<String?> {
        Binding(
            get: { placedRange },
            set: { range in if let range { place(range, animated: true) } }
        )
    }

    private func place(_ range: String, animated: Bool) {
        guard let chart = history.chart else { return }
        let ids = history.shownIds
        let start = Date(epochSeconds: chart.rangeStartEpochSeconds(range: range, shownIds: ids))
        let end = Date(epochSeconds: chart.rangeEndEpochSeconds(range: range, shownIds: ids))
        let move = {
            domain = start ... max(end, start.addingTimeInterval(3600))
            placedRange = range
        }
        if animated {
            withAnimation(.smooth(duration: 0.45), move)
        } else {
            move()
        }
    }

    static func rangeLabel(_ range: String) -> LocalizedStringKey {
        switch range {
        case HistoryRanges.shared.ONE_MONTH: "1M"
        case HistoryRanges.shared.THREE_MONTHS: "3M"
        case HistoryRanges.shared.ONE_YEAR: "1Y"
        default: "Period"
        }
    }
}

/// Where the lead limit is heading. Its own view, so sliding the chart does not redo the sentence.
private struct ProjectionText: View {
    let lead: HistoryWindowUi
    let showsLeft: Bool
    @ScaledMetric(relativeTo: .footnote) private var minHeight = 34.0

    var body: some View {
        Text(PaceTexts.projection(lead.summary, showsLeft: showsLeft))
            .alloFont(.footnote)
            .foregroundStyle(.secondary)
            .frame(minHeight: minHeight, alignment: .topLeading)
            .contentTransition(.opacity)
            .animation(.easeInOut(duration: 0.2), value: lead.id)
    }
}

/// A limit's toggle: its colour and marker, filled while the limit is on the chart.
private struct LimitChip: View {
    let label: String
    let slot: Int
    let providerId: String
    let isOn: Bool
    let action: () -> Void
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let color = ProviderColors.part(providerId: providerId, slot: slot, colorScheme: colorScheme)
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: isOn ? Self.symbols[slot % Self.symbols.count] : "circle.dashed")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(isOn ? color : .secondary)
                    .contentTransition(.symbolEffect(.replace))
                Text(label)
                    .alloFont(.subheadline)
                    .foregroundStyle(isOn ? .primary : .secondary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background {
                Capsule()
                    .fill(isOn ? color.opacity(colorScheme == .dark ? 0.24 : 0.14) : .clear)
                    .strokeBorder(isOn ? color.opacity(0.5) : Color.secondary.opacity(0.4), lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.selection, trigger: isOn)
        .accessibilityAddTraits(isOn ? .isSelected : [])
        .accessibilityIdentifier("chart-limit-\(label)")
    }

    /// The SF Symbols of the chart's markers, in the same order.
    private static let symbols = ["circle.fill", "square.fill", "diamond.fill", "triangle.fill", "pentagon.fill", "plus"]
}
