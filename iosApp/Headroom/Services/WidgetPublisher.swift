import HeadroomKit
import WidgetKit

/// Writes what the widgets show to the App Group, and asks them to redraw when it changed.
@MainActor
enum WidgetPublisher {
    static func publish(overview: OverviewUi, providers: [ProviderUi]) {
        let snapshot = WidgetSnapshot(
            isDemo: overview.isDemo,
            showsLeft: overview.display == "left",
            accounts: overview.accounts.map { account in
                let provider = providers.first { $0.id == account.providerId }
                return WidgetSnapshot.Account(
                    id: account.id,
                    providerId: account.providerId,
                    title: account.title,
                    windowLabel: account.primary?.label,
                    usedPercent: account.primary?.usedPercent ?? 0,
                    resetsAt: account.primary?.resetsAtEpochSeconds.map { Date(epochSeconds: $0.int64Value) },
                    needsAttention: account.needsAttention,
                    signInExpired: account.signInExpired,
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
                    accountTitle: next.accountTitle,
                    providerId: overview.accounts.first { $0.id == next.accountId }?.providerId ?? "",
                    windowLabel: next.windowLabel,
                    resetsAt: Date(epochSeconds: next.resetsAtEpochSeconds)
                )
            },
            nextResetTile: overview.nextResetTile.map { WidgetSnapshot.Tile(name: $0.name, value: $0.value) },
            tightestTile: overview.tightestTile.map { WidgetSnapshot.Tile(name: $0.name, value: $0.value) }
        )
        guard snapshot != WidgetSnapshot.load() else { return }
        snapshot.save()
        WidgetCenter.shared.reloadAllTimelines()
        ControlCenter.shared.reloadAllControls()
    }
}
