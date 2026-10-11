import WidgetKit

struct SnapshotEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot
}

/// Reads the snapshot the app wrote, for the accounts the widget was set up with. The widget redraws
/// when the app writes a new one, at the next reset, and every half hour so "resets in" stays close.
struct SnapshotProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry {
        SnapshotEntry(date: .now, snapshot: .preview)
    }

    func snapshot(for configuration: AccountsConfiguration, in context: Context) async -> SnapshotEntry {
        let snapshot = WidgetSnapshot.load()
        let shown = context.isPreview && snapshot.accounts.isEmpty ? .preview : snapshot
        return SnapshotEntry(date: .now, snapshot: shown.selecting(configuration.accounts?.map(\.id)))
    }

    func timeline(for configuration: AccountsConfiguration, in context: Context) async -> Timeline<SnapshotEntry> {
        let snapshot = WidgetSnapshot.load().selecting(configuration.accounts?.map(\.id))
        var next = Date.now.addingTimeInterval(30 * 60)
        if let reset = snapshot.nextReset?.resetsAt, reset > .now, reset < next {
            next = reset
        }
        return Timeline(entries: [SnapshotEntry(date: .now, snapshot: snapshot)], policy: .after(next))
    }
}
