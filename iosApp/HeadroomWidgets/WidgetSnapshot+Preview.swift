import Foundation

extension WidgetSnapshot {
    /// What the widget gallery shows before the app wrote a snapshot.
    static let preview = WidgetSnapshot(
        isDemo: false,
        showsLeft: false,
        accounts: [
            Account(id: "claude", providerId: "claude", title: "Claude", windowLabel: "Weekly", usedPercent: 62,
                    resetsAt: .now.addingTimeInterval(2 * 86_400), needsAttention: false, signInExpired: false, logo: nil),
            Account(id: "codex", providerId: "codex", title: "Codex", windowLabel: "Weekly", usedPercent: 38,
                    resetsAt: .now.addingTimeInterval(4 * 86_400), needsAttention: false, signInExpired: false, logo: nil),
            Account(id: "grok", providerId: "grok", title: "Grok", windowLabel: "Weekly", usedPercent: 88,
                    resetsAt: .now.addingTimeInterval(86_400), needsAttention: true, signInExpired: false, logo: nil),
        ],
        nextReset: NextReset(accountTitle: "Grok", providerId: "grok", windowLabel: "Weekly",
                             resetsAt: .now.addingTimeInterval(86_400)),
        nextResetTile: Tile(name: "Grok", value: "in 1d"),
        tightestTile: Tile(name: "Grok", value: "88% used")
    )
}
