import HeadroomKit
import SwiftUI

/// The detail screen's pace chart, for the window the user picks, and where the window is heading.
struct PaceChartSection: View {
    let account: AccountUi
    let windows: [WindowUi]
    @Environment(AppModel.self) private var model
    @State private var windowId: String?
    @State private var chartModel = ChartModel()

    var body: some View {
        if !charted.isEmpty {
            chartSection
        }
    }

    private var chartSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 10) {
                if charted.count > 1 {
                    Picker("Window", selection: Binding(get: { selectedWindowId }, set: { windowId = $0 })) {
                        ForEach(charted) { window in
                            Text(PaceTexts.chartWindowLabel(window.label, providerId: account.providerId))
                                .tag(Optional(window.id))
                        }
                    }
                    .pickerStyle(.segmented)
                }
                if let chart = chartModel.chart {
                    PaceChart(chart: chart, showsLeft: model.showsLeft, providerId: account.providerId)
                        .frame(height: 180)
                        .padding(.vertical, 8)
                    Text("dashed = even pace")
                        .alloFont(.caption)
                        .foregroundStyle(.secondary)
                    Text(PaceTexts.projection(chart, showsLeft: model.showsLeft))
                        .alloFont(.footnote)
                        .foregroundStyle(.secondary)
                } else if chartModel.loaded {
                    Text("No history for this window yet.")
                        .foregroundStyle(.secondary)
                } else {
                    ProgressView()
                        .frame(maxWidth: .infinity, minHeight: 180)
                }
            }
        } header: {
            Text(chartModel.chart.map { PaceTexts.title($0.kind) } ?? "This window")
        }
        .onChange(of: selectedWindowId, initial: true) { _, id in
            if let id { chartModel.show(model.headroom, accountId: account.id, windowId: id) }
        }
        .onDisappear(perform: chartModel.stop)
    }

    private var selectedWindowId: String? {
        let preferred = windowId ?? account.primary?.id
        return charted.first { $0.id == preferred }?.id ?? charted.first?.id
    }

    /// The windows a chart makes sense for: those that reset, with a pace.
    private var charted: [WindowUi] {
        windows.filter { $0.resetsAtEpochSeconds != nil && !$0.isInformational }
    }
}
