import HeadroomKit
import SwiftUI

/// The detail screen's pace chart, for the window the user picks, and where the window is heading.
struct PaceChartSection: View {
    let account: AccountUi
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
            if charted.count > 1 {
                Picker("Window", selection: $windowId) {
                    ForEach(charted) { window in
                        Text(window.label).tag(Optional(window.id))
                    }
                }
                .pickerStyle(.segmented)
            }
            if let chart = chartModel.chart {
                PaceChart(chart: chart, showsLeft: model.showsLeft, providerId: account.providerId)
                    .frame(height: 180)
                    .padding(.vertical, 8)
                Text(PaceTexts.projection(chart, showsLeft: model.showsLeft))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            } else if chartModel.loaded {
                Text("No history for this window yet.")
                    .foregroundStyle(.secondary)
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, minHeight: 180)
            }
        } header: {
            Text(chartModel.chart.map { PaceTexts.title($0.kind) } ?? "This window")
        }
        .onAppear {
            if windowId == nil { windowId = charted.first?.id }
        }
        .onChange(of: windowId, initial: true) { _, id in
            if let id { chartModel.show(model.headroom, accountId: account.id, windowId: id) }
        }
        .onDisappear(perform: chartModel.stop)
    }

    /// The windows a chart makes sense for: those that reset, with a pace.
    private var charted: [WindowUi] {
        account.windows.filter { $0.resetsAtEpochSeconds != nil && !$0.isInformational && !$0.isUnlimited }
    }
}
