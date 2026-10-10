import Foundation
import HeadroomKit
import Observation

/// The app's state: what HeadroomKit shows, kept up to date by its callbacks, which arrive on the
/// main thread. The actions forward to HeadroomKit.
@MainActor
@Observable
final class AppModel {
    private(set) var overview: OverviewUi?
    private(set) var settings: SettingsUi?
    private(set) var stats: StatsUi?
    private(set) var resetsTab: ResetsTabUi?
    private(set) var signInStep: any SignInUi = SignInUiIdle.shared
    private(set) var redeemStep: any RedeemUi = RedeemUiIdle.shared
    private(set) var zCodeStep: any ZCodeUi = ZCodeUiIdle.shared
    let providers: [ProviderUi]
    /// Where a notification tap asked to go, until the overview goes there.
    var route: Route?

    enum Route: Equatable {
        case account(String)
        case signIn(accountId: String)
    }

    @ObservationIgnored let headroom: Headroom
    @ObservationIgnored private var watches: [Watch] = []

    init(headroom: Headroom) {
        self.headroom = headroom
        providers = headroom.providers
        watch(headroom.watchOverview) { $0.overview = $1 }
        watch(headroom.settings.watch) { $0.settings = $1 }
        watch(headroom.stats.watch) { $0.stats = $1 }
        watch(headroom.resets.watchTab) { $0.resetsTab = $1 }
        watch(headroom.signIn.watch) { $0.signInStep = $1 }
        watch(headroom.resets.redeem.watch) { $0.redeemStep = $1 }
        watch(headroom.resets.zCode.watch) { $0.zCodeStep = $1 }
    }

    /// Opens Headroom with its database in Application Support and the sign-ins in the Keychain.
    static func live() -> AppModel {
        let support = URL.applicationSupportDirectory.appending(path: "Headroom", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: support, withIntermediateDirectories: true)
        return AppModel(headroom: OpenHeadroomKt.openHeadroom(directory: support.path(percentEncoded: false),
                                                               secrets: KeychainStore()))
    }

    /// Keeps `apply` running on every value `subscribe` delivers, which is on the main thread.
    private func watch<T>(_ subscribe: (@escaping (T) -> Void) -> Watch, apply: @escaping (AppModel, T) -> Void) {
        watches.append(subscribe { [weak self] value in
            MainActor.assumeIsolated {
                if let self { apply(self, value) }
            }
        })
    }

    func open(_ route: Route) {
        self.route = route
    }

    func provider(id: String) -> ProviderUi? {
        providers.first { $0.id == id }
    }

    func account(id: String) -> AccountUi? {
        overview?.accounts.first { $0.id == id }
    }

    /// Shows how much is used or how much is left, as the user chose.
    var showsLeft: Bool {
        (settings?.quotaDisplay ?? overview?.display) == "left"
    }

    func refresh(accountId: String? = nil) {
        headroom.refresh(accountId: accountId)
    }

    /// Syncs every account and returns when it is done.
    func refreshAll() async {
        try? await headroom.refreshAll()
    }
}
