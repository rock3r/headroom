import HeadroomKit
import WidgetKit

/// Writes what the widgets show to the App Group, and asks them to redraw when it changed.
@MainActor
enum WidgetPublisher {
    /// "in 2d 4h", "88% used" or "12% left", as the Android tile words them.
    static func tile(_ tile: TileUi) -> WidgetSnapshot.Tile {
        let value = switch tile.kind {
        case "countdown": String(localized: "in \(tile.value)")
        case "left": String(localized: "\(tile.value)% left")
        default: String(localized: "\(tile.value)% used")
        }
        return WidgetSnapshot.Tile(name: tile.name, value: value)
    }

    /// The nickname, the label when the provider appears more than once, or the provider's short
    /// name, as the Android widgets name accounts.
    private static func name(_ account: AccountUi, repeated: Bool) -> String {
        if account.title != account.providerName { return account.title }
        if repeated { return account.label }
        return shortNames[account.providerId] ?? account.providerName
    }

    private static let shortNames = [
        "claude": "Claude", "codex": "Codex", "copilot": "Copilot", "grok": "Grok", "kimi": "Kimi",
        "zai": "Z.AI", "opencode-go": "OpenCode", "jetbrains": "JetBrains",
    ]

    static func publish(overview: OverviewUi, providers: [ProviderUi]) {
        let repeated = Dictionary(grouping: overview.accounts, by: \.providerId).filter { $0.value.count > 1 }.keys
        let snapshot = WidgetSnapshot(
            isDemo: overview.isDemo,
            showsLeft: overview.display == "left",
            accounts: overview.accounts.map { account in
                let provider = providers.first { $0.id == account.providerId }
                let session = account.windows.first { $0.kind == "session" && $0.id != account.primary?.id }
                return WidgetSnapshot.Account(
                    id: account.id,
                    providerId: account.providerId,
                    name: name(account, repeated: repeated.contains(account.providerId)),
                    kind: account.primary?.kind ?? "other",
                    usedPercent: account.primary?.usedPercent ?? 0,
                    expectedPercent: account.primary?.expectedPercent?.doubleValue,
                    resetsAt: account.primary?.resetsAtEpochSeconds.map { Date(epochSeconds: $0.int64Value) },
                    needsAttention: account.needsAttention,
                    signInExpired: account.signInExpired,
                    sessionUsedPercent: session?.usedPercent,
                    resetsAvailable: Int(account.resets?.availableNow ?? 0),
                    logo: provider.map {
                        WidgetSnapshot.Logo(
                            pathData: $0.logoPath,
                            viewport: Double($0.logoViewport),
                            inset: Double($0.logoInset)
                        )
                    }
                )
            },
            nextReset: overview.nextReset.map { next in
                WidgetSnapshot.NextReset(
                    accountId: next.accountId,
                    accountTitle: next.accountTitle,
                    providerId: overview.accounts.first { $0.id == next.accountId }?.providerId ?? "",
                    windowLabel: next.windowLabel,
                    kind: next.kind,
                    resetsAt: Date(epochSeconds: next.resetsAtEpochSeconds)
                )
            },
            nextResetTile: overview.nextResetTile.map(tile),
            tightestTile: overview.tightestTile.map(tile)
        )
        guard snapshot != WidgetSnapshot.load() else { return }
        snapshot.save()
        WidgetCenter.shared.reloadAllTimelines()
        ControlCenter.shared.reloadAllControls()
    }
}
