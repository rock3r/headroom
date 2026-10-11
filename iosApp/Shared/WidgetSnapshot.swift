import Foundation

/// What the widgets and the control show: the app writes it after every change, the widgets read
/// it. Widgets cannot sync by themselves, so they show the numbers of the app's last sync.
struct WidgetSnapshot: Codable, Equatable {
    struct Account: Codable, Equatable, Identifiable {
        let id: String
        let providerId: String
        let title: String
        let windowLabel: String?
        let usedPercent: Double
        let resetsAt: Date?
        let needsAttention: Bool
        let signInExpired: Bool
        let logo: Logo?

        /// A rejected sign-in has historical usage, but no current percentage to announce.
        var currentUsedPercent: Double? { signInExpired ? nil : usedPercent }

        func at(_ date: Date) -> Self {
            Self(id: id, providerId: providerId, title: title, windowLabel: windowLabel,
                 usedPercent: usedPercent, resetsAt: resetsAt.flatMap { $0 > date ? $0 : nil },
                 needsAttention: needsAttention, signInExpired: signInExpired, logo: logo)
        }
    }

    struct Logo: Codable, Equatable {
        let pathData: String
        let viewport: Double
        let inset: Double
    }

    struct NextReset: Codable, Equatable {
        let accountTitle: String
        let providerId: String
        let windowLabel: String
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

    /// The share of `account`'s main limit to show: used, or left.
    func shown(_ account: Account) -> Double {
        showsLeft ? 100 - account.usedPercent : account.usedPercent
    }

    /// The account closest to its limit, which the small widgets lead with.
    var tightest: Account? {
        accounts.filter { !$0.signInExpired }.max { $0.usedPercent < $1.usedPercent }
    }

    /// A widget may reload while the app still has an old snapshot. Never count down to the past.
    func at(_ date: Date) -> Self {
        let upcoming = nextReset.flatMap { $0.resetsAt > date ? $0 : nil }
        return Self(isDemo: isDemo, showsLeft: showsLeft, accounts: accounts.map { $0.at(date) },
            nextReset: upcoming, nextResetTile: upcoming == nil ? nil : nextResetTile, tightestTile: tightestTile)
    }
}
