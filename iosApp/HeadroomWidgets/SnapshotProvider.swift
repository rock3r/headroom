import WidgetKit

struct SnapshotEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot
}

/// Reads the snapshot the app wrote. The widget redraws when the app writes a new one, at the next
/// reset, and every half hour so "resets in" stays close.
struct SnapshotProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry {
        SnapshotEntry(date: .now, snapshot: .preview)
    }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        let snapshot = WidgetSnapshot.load()
        completion(SnapshotEntry(date: .now, snapshot: context.isPreview && snapshot.isDemo ? .preview : snapshot))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        let snapshot = WidgetSnapshot.load()
        var next = Date.now.addingTimeInterval(30 * 60)
        if let reset = snapshot.nextReset?.resetsAt, reset > .now, reset < next {
            next = reset
        }
        completion(Timeline(entries: [SnapshotEntry(date: .now, snapshot: snapshot)], policy: .after(next)))
    }
}
