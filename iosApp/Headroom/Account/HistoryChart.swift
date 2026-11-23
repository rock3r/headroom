import Charts
import HeadroomKit
import SwiftUI

/// The account's weekly and monthly windows on one time axis, as the Android usage history chart:
/// past resets as dashed rules, the lead window's current period shaded, each current period's
/// even-pace diagonal and where usage is heading. Drag sideways to move through the history;
/// long-press to read the values at a moment. With "Show quotas as left", it shows how much is
/// left instead.
struct HistoryChart: View {
    let chart: HistoryChartUi
    let shown: [HistoryWindowUi]
    let lead: HistoryWindowUi?
    /// Each window's history as Swift values, by window id.
    let series: [String: HistorySeries]
    let showsLeft: Bool
    let providerId: String
    /// The stretch of time on screen. Changing it inside an animation slides the plot there.
    @Binding var domain: ClosedRange<Date>
    /// The user started dragging the chart away from where it was placed.
    var onPan: () -> Void = {}
    @Environment(\.colorScheme) private var colorScheme
    @State private var selected: Date?
    /// Where the domain was when a sideways drag started, or nil while no drag is moving it.
    @State private var panOrigin: ClosedRange<Date>?
    /// The domain the drag or its glide last set, to notice something else moving the chart.
    @State private var panned: ClosedRange<Date>?
    /// The drag has moved the chart, and said so through `onPan`.
    @State private var hasMoved = false

    var body: some View {
        Chart {
            if let lead {
                RectangleMark(xStart: .value("Start", date(lead.summary.startEpochSeconds)),
                              xEnd: .value("End", date(lead.summary.endEpochSeconds)),
                              yStart: .value("Low", 0), yEnd: .value("High", 100))
                    .foregroundStyle(color(of: lead).opacity(colorScheme == .dark ? 0.08 : 0.06))
            }
            resetRules
            paceLines
            ForEach(shown, id: \.id) { window in
                usageLine(window)
                projection(window)
            }
            RuleMark(y: .value("Limit", plotted(100)))
                .foregroundStyle(.red.opacity(0.5))
                .lineStyle(StrokeStyle(lineWidth: 1, dash: [2, 3]))
                .annotation(position: showsLeft ? .top : .bottom, alignment: .trailing) {
                    Text("limit")
                        .alloFont(.caption2)
                        .foregroundStyle(.red)
                }
            if let selected {
                RuleMark(x: .value("Selected", selected))
                    .foregroundStyle(Color.secondary)
                    .lineStyle(StrokeStyle(lineWidth: 1))
            }
        }
        .chartXScale(domain: domain)
        // Marks outside the domain would otherwise draw over the axes while the chart slides.
        .chartPlotStyle { $0.clipped() }
        .chartOverlay { proxy in
            GeometryReader { geometry in
                let plot = proxy.plotFrame.map { geometry[$0] } ?? geometry.frame(in: .local)
                Rectangle()
                    .fill(.clear)
                    .contentShape(Rectangle())
                    .gesture(PressAndDrag { point in
                        selected = point.flatMap { proxy.value(atX: $0.x - plot.minX, as: Date.self) }
                    })
                    .gesture(SidewaysPan { travel in pan(by: travel, width: plot.width) })
                if let selected, let x = proxy.position(forX: selected) {
                    let width = Self.tooltipWidth
                    tooltip(at: selected)
                        .frame(width: width)
                        .offset(x: min(max(plot.minX + x - width / 2, 0), geometry.size.width - width), y: plot.minY + 4)
                        .allowsHitTesting(false)
                }
            }
        }
        .chartXAxis { xAxis }
        .chartYScale(domain: 0 ... 100)
        .chartYAxis {
            AxisMarks(values: [0, 50, 100]) { value in
                AxisGridLine()
                AxisValueLabel {
                    if let percent = value.as(Int.self) { Text("\(percent)%") }
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
        .accessibilityIdentifier("usage-history-chart")
    }

    // MARK: Marks

    @ChartContentBuilder
    private func usageLine(_ window: HistoryWindowUi) -> some ChartContent {
        let color = color(of: window)
        let samples = samples(of: window)
        // One vectorized plot per line: thousands of separate marks make sliding the chart stutter.
        // Each window carries its own series id so Swift Charts never joins one window's end to the next's start.
        LinePlot(
            samples,
            x: .value("Time", \.date),
            y: .value("Usage", showsLeft ? \.left : \.used),
            series: .value("Window", \.series)
        )
            .foregroundStyle(color)
            .lineStyle(StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round))
        if let last = samples.last {
            PointMark(x: .value("Time", last.date),
                      y: .value("Usage", plotted(last.used)))
                .symbol(symbol(of: window))
                .symbolSize(48)
                .foregroundStyle(color)
        }
    }

    /// Where the window is heading: to the limit, or to where it ends at the reset.
    @ChartContentBuilder
    private func projection(_ window: HistoryWindowUi) -> some ChartContent {
        if let target = projectionTarget(window) {
            let from = (date(window.sampleAtEpochSeconds), min(max(window.summary.usedPercent, 0), 100))
            ForEach([from, target], id: \.0) { point in
                LineMark(x: .value("Time", point.0),
                         y: .value("Usage", plotted(point.1)),
                         series: .value("Projection", "projection-\(window.id)"))
                    .foregroundStyle(color(of: window).opacity(0.8))
                    .lineStyle(StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: [1, 4]))
            }
        }
    }

    /// One dashed diagonal per distinct current period.
    @ChartContentBuilder
    private var paceLines: some ChartContent {
        ForEach(periods, id: \.self) { period in
            ForEach([(period.start, 0.0), (period.end, 100.0)], id: \.0) { point in
                LineMark(x: .value("Time", date(point.0)),
                         y: .value("Even pace", plotted(point.1)),
                         series: .value("Pace", "pace-\(period.start)-\(period.end)"))
                    .foregroundStyle(Color.secondary)
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
            }
        }
    }

    /// Each shown window's past resets, thinned when a long range would pack them together.
    @ChartContentBuilder
    private var resetRules: some ChartContent {
        ForEach(resets, id: \.at) { reset in
            RuleMark(x: .value("Reset", date(reset.at)))
                .foregroundStyle(reset.color.opacity(0.55))
                .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
        }
    }

    /// Moves through the history by a sideways drag's `travel`, from where the drag started.
    /// Returns false once the history's edge stops it.
    private func pan(by travel: CGFloat?, width: CGFloat) -> Bool {
        guard let travel, selected == nil, width > 0 else {
            panOrigin = nil
            panned = nil
            hasMoved = false
            return false
        }
        // A range preset or a new period moved the chart: it wins over the drag and its glide.
        if let panned, panned != domain { return false }
        let origin = panOrigin ?? domain
        panOrigin = origin
        let span = origin.upperBound.timeIntervalSince(origin.lowerBound)
        let lower = date(chart.minEpochSeconds).timeIntervalSince(origin.lowerBound)
        let upper = date(chart.maxEpochSeconds).timeIntervalSince(origin.upperBound)
        let wanted = -travel / width * span
        let shift = min(max(wanted, min(lower, 0)), max(upper, 0))
        // Only a drag that moves the chart takes it off its preset: one against an edge does not.
        if shift != 0, !hasMoved {
            hasMoved = true
            onPan()
        }
        domain = origin.lowerBound.addingTimeInterval(shift) ... origin.upperBound.addingTimeInterval(shift)
        panned = domain
        return shift == wanted
    }

    private var visibleLength: TimeInterval {
        domain.upperBound.timeIntervalSince(domain.lowerBound)
    }

    private var xAxis: some AxisContent {
        let days = visibleLength / 86_400
        return AxisMarks(values: .automatic(desiredCount: 5)) { value in
            AxisGridLine()
            AxisValueLabel {
                if let date = value.as(Date.self) {
                    if days <= 8 {
                        Text(date, format: .dateTime.weekday(.abbreviated))
                    } else if days <= 100 {
                        Text(date, format: .dateTime.day().month(.abbreviated))
                    } else {
                        Text(date, format: .dateTime.month(.abbreviated))
                    }
                }
            }
        }
    }

    private func tooltip(at selected: Date) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(selected, format: .dateTime.weekday(.abbreviated).day().month(.abbreviated).hour().minute())
                .alloFont(.caption2)
                .foregroundStyle(.secondary)
            ForEach(shown, id: \.id) { window in
                if let point = series[window.id]?.sample(at: selected) {
                    HStack(spacing: 4) {
                        Image(systemName: "circle.fill")
                            .font(.system(size: 6))
                            .foregroundStyle(color(of: window))
                        Text(PaceTexts.chartWindowLabel(window.label, providerId: providerId))
                        Spacer(minLength: 8)
                        Text("\(Int(plotted(point.used).rounded()))%")
                            .monospacedDigit()
                    }
                    .alloFont(.caption)
                }
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
    }

    // MARK: Data

    private struct Period: Hashable {
        let start: Int64
        let end: Int64
    }

    private var periods: [Period] {
        var seen = Set<Period>()
        return shown.compactMap { window in
            let period = Period(start: window.summary.startEpochSeconds, end: window.summary.endEpochSeconds)
            return period.end > period.start && seen.insert(period).inserted ? period : nil
        }
    }

    private struct Reset {
        let at: Int64
        let color: Color
    }

    private var resets: [Reset] {
        var seen = Set<Int64>()
        let thin = visibleLength > 45 * 86_400
        return shown.flatMap { window in
            let all = series[window.id]?.resets ?? []
            // Keep every fourth, counting back from the latest so the newest is always marked.
            let stride = thin && all.count > 20 ? 4 : 1
            let first = all.isEmpty ? 0 : (all.count - 1) % stride
            return Swift.stride(from: first, to: all.count, by: stride).compactMap { index in
                seen.insert(all[index]).inserted ? Reset(at: all[index], color: color(of: window)) : nil
            }
        }
    }

    private func samples(of window: HistoryWindowUi) -> [HistorySample] {
        series[window.id]?.samples ?? []
    }

    private func projectionTarget(_ window: HistoryWindowUi) -> (Date, Double)? {
        let summary = window.summary
        guard summary.usedPercent > 0.5 else { return nil }
        if let limit = summary.projectedLimitAtEpochSeconds?.int64Value, limit > window.sampleAtEpochSeconds {
            return (date(limit), 100)
        }
        if let end = summary.projectedEndPercent?.doubleValue, summary.endEpochSeconds > window.sampleAtEpochSeconds {
            return (date(summary.endEpochSeconds), min(max(end, 0), 100))
        }
        return nil
    }

    // MARK: Style

    private var lineWidth: CGFloat {
        let days = visibleLength / 86_400
        return days <= 45 ? 2.5 : days <= 180 ? 1.75 : 1.2
    }

    private func slot(of window: HistoryWindowUi) -> Int {
        chart.windows.firstIndex { $0.id == window.id } ?? 0
    }

    func color(of window: HistoryWindowUi) -> Color {
        ProviderColors.part(providerId: providerId, slot: slot(of: window), colorScheme: colorScheme)
    }

    /// A distinct marker per window, so colour is never the only cue.
    private func symbol(of window: HistoryWindowUi) -> BasicChartSymbolShape {
        HistoryChart.symbols[slot(of: window) % HistoryChart.symbols.count]
    }

    private static let tooltipWidth: CGFloat = 170

    static let symbols: [BasicChartSymbolShape] = [.circle, .square, .diamond, .triangle, .pentagon, .cross]

    private var accessibilityText: Text {
        guard let lead else { return Text("Usage history") }
        let names = shown.map { PaceTexts.chartWindowLabel($0.label, providerId: providerId) }
            .formatted(.list(type: .and))
        return Text("\(names). ") + PaceTexts.description(lead.summary, showsLeft: showsLeft)
    }

    private func date(_ epochSeconds: Int64) -> Date {
        Date(epochSeconds: epochSeconds)
    }

    private func plotted(_ used: Double) -> Double {
        showsLeft ? 100 - used : used
    }
}
