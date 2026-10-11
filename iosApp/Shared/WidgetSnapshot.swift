import Foundation

/// What the widgets and the control show: the app writes it after every change, the widgets read
/// it. Widgets cannot sync by themselves, so they show the numbers of the app's last sync.
struct WidgetSnapshot: Codable, Equatable {
    struct Account: Codable, Equatable, Identifiable {
        let id: String
        let providerId: String
        /// The nickname, the label when a provider appears more than once, or the provider's short
        /// name, as the Android widgets name accounts.
        let name: String
        /// `weekly`, `monthly` or another kind: the main window's.
        let kind: String
        let usedPercent: Double
        /// Where usage would be at even pace, for the bars' tick.
        let expectedPercent: Double?
        let resetsAt: Date?
        let needsAttention: Bool
        let signInExpired: Bool
        /// The session window inside the ring, when the account has one.
        let sessionUsedPercent: Double?
        let resetsAvailable: Int
        let logo: Logo?
    }

    struct Logo: Codable, Equatable {
        let pathData: String
        let viewport: Double
        let inset: Double
    }

    struct NextReset: Codable, Equatable {
        let accountId: String
        let accountTitle: String
        let providerId: String
        let windowLabel: String
        let kind: String
        let resetsAt: Date
    }

    /// "Claude" and "in 2d 4h": the account and the value, as the Android tile shows them.
    struct Tile: Codable, Equatable {
        let name: String
        let value: String
    }

    let isDemo: Bool
    let showsLeft: Bool
    /// In the overview's order.
    let accounts: [Account]
    let nextReset: NextReset?
    let nextResetTile: Tile?
    let tightestTile: Tile?

    static let empty = WidgetSnapshot(
        isDemo: true, showsLeft: false, accounts: [], nextReset: nil, nextResetTile: nil, tightestTile: nil
    )

    private static var file: URL? {
        AppGroup.container?.appending(path: "widget-snapshot.json")
    }

    static func load() -> WidgetSnapshot {
        guard let file, let data = try? Data(contentsOf: file) else { return .empty }
        return (try? JSONDecoder().decode(WidgetSnapshot.self, from: data)) ?? .empty
    }

    func save() {
        guard let file = Self.file, let data = try? JSONEncoder().encode(self) else { return }
        try? data.write(to: file, options: .atomic)
    }

    /// The share to show: used, or left.
    func shown(_ used: Double) -> Double {
        showsLeft ? 100 - used : used
    }

    /// The account closest to its limit, which the small widgets lead with.
    var tightest: Account? {
        accounts.filter { !$0.signInExpired }.max { $0.usedPercent < $1.usedPercent } ?? accounts.first
    }

    /// The same snapshot with only `ids`, in that order, or every account when `ids` is nil.
    func selecting(_ ids: [String]?) -> WidgetSnapshot {
        guard let ids, !ids.isEmpty else { return self }
        let byId = Dictionary(uniqueKeysWithValues: accounts.map { ($0.id, $0) })
        let chosen = ids.compactMap { byId[$0] }
        let next = nextReset.flatMap { next in chosen.contains { $0.id == next.accountId } ? next : nil }
            ?? chosen.compactMap { account in
                account.resetsAt.map { (account, $0) }
            }
            .filter { $0.1 > .now }
            .min { $0.1 < $1.1 }
            .map { NextReset(accountId: $0.0.id, accountTitle: $0.0.name, providerId: $0.0.providerId,
                             windowLabel: "", kind: $0.0.kind, resetsAt: $0.1) }
        return WidgetSnapshot(isDemo: isDemo, showsLeft: showsLeft, accounts: chosen, nextReset: next,
                              nextResetTile: nextResetTile, tightestTile: tightestTile)
    }
}
