import Foundation

extension WidgetSnapshot {
    /// What the widget gallery shows before the app wrote a snapshot.
    static let preview = WidgetSnapshot(
        isDemo: false,
        showsLeft: false,
        accounts: [
            preview("claude", "Claude", used: 62, session: 38, days: 2, resets: 0),
            preview("codex", "Codex", used: 35, session: 12, days: 4, resets: 2),
            preview("grok", "Grok", used: 88, session: nil, days: 1, resets: 0, attention: true),
            preview("copilot", "Copilot", used: 20, session: nil, days: 12, resets: 0),
        ],
        nextReset: NextReset(accountId: "grok", accountTitle: "Grok", providerId: "grok", windowLabel: "Weekly",
                             kind: "weekly", resetsAt: .now.addingTimeInterval(86_400)),
        nextResetTile: Tile(name: "Grok", value: "in 1d"),
        tightestTile: Tile(name: "Grok", value: "88% used")
    )

    private static func preview(_ id: String, _ name: String, used: Double, session: Double?, days: Double,
                                resets: Int, attention: Bool = false) -> Account {
        Account(id: id, providerId: id, name: name, kind: "weekly", usedPercent: used, expectedPercent: 50,
                resetsAt: .now.addingTimeInterval(days * 86_400), needsAttention: attention, signInExpired: false,
                sessionUsedPercent: session, resetsAvailable: resets, logo: nil)
    }
}
