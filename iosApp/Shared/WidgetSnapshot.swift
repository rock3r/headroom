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

        var currentUsedPercent: Double? { signInExpired ? nil : usedPercent }

        func at(_ date: Date) -> Self {
            Self(id: id, providerId: providerId, name: name, kind: kind, usedPercent: usedPercent,
                 expectedPercent: expectedPercent, resetsAt: resetsAt.flatMap { $0 > date ? $0 : nil },
                 needsAttention: needsAttention, signInExpired: signInExpired,
                 sessionUsedPercent: sessionUsedPercent, resetsAvailable: resetsAvailable, logo: logo)
        }
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
    /// All alertable windows, retained so each selection can match Headroom's weekly priority.
    /// Missing in snapshots written by older versions.
    var resetCandidates: [NextReset]? = nil

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

    /// The inline presentation shows one account, so its tap opens that account or its sign-in.
    var inlineDestination: URL {
        if let nextReset { return DeepLink.account(nextReset.accountId) }
        guard let account = tightest else { return DeepLink.overview }
        return account.signInExpired ? DeepLink.signIn(account.id) : DeepLink.account(account.id)
    }

    /// The same snapshot with only `ids`, in that order, or every account when `ids` is nil. The next
    /// reset skips expired sign-ins, whose reset times are stale, as the app's next reset does.
    func selecting(_ ids: [String]?, at date: Date = .now) -> WidgetSnapshot {
        let byId = Dictionary(uniqueKeysWithValues: accounts.map { ($0.id, $0) })
        let chosen = ids.flatMap { $0.isEmpty ? nil : $0.compactMap { byId[$0] } } ?? accounts
        let eligibleIds = Set(chosen.filter { !$0.signInExpired }.map(\.id))
        let candidates = resetCandidates ?? legacyCandidates
        let eligible = candidates.filter {
            eligibleIds.contains($0.accountId) && $0.resetsAt > date &&
                ($0.kind == "weekly" || $0.kind == "monthly")
        }
        let weekly = eligible.filter { $0.kind == "weekly" }
        let next = (weekly.isEmpty ? eligible : weekly).min { $0.resetsAt < $1.resetsAt }
        return WidgetSnapshot(isDemo: isDemo, showsLeft: showsLeft, accounts: chosen.map { $0.at(date) }, nextReset: next,
                              nextResetTile: next == nextReset ? nextResetTile : nil, tightestTile: tightestTile,
                              resetCandidates: resetCandidates?.filter { candidate in chosen.contains { $0.id == candidate.accountId } })
    }

    /// Drop elapsed resets even when the app has not published another snapshot.
    func at(_ date: Date) -> Self {
        selecting(nil, at: date)
    }

    private var legacyCandidates: [NextReset] {
        accounts.compactMap { account in
            account.resetsAt.map { NextReset(accountId: account.id, accountTitle: account.name,
                providerId: account.providerId, windowLabel: "", kind: account.kind, resetsAt: $0) }
        } + (nextReset.map { [$0] } ?? [])
    }
}
