import Foundation
import HeadroomKit
import Observation

/// The app's state: HeadroomKit's overview and sign-in step, kept up to date by its callbacks, which
/// arrive on the main thread.
@MainActor
@Observable
final class AppModel {
    private(set) var overview: OverviewUi?
    private(set) var signInStep: any SignInUi = SignInUiIdle.shared
    let providers: [ProviderUi]

    @ObservationIgnored private let headroom: Headroom
    @ObservationIgnored private var watches: [Watch] = []

    init(headroom: Headroom) {
        self.headroom = headroom
        providers = headroom.providers
        watches.append(headroom.watchOverview { [weak self] overview in
            MainActor.assumeIsolated { self?.overview = overview }
        })
        watches.append(headroom.signIn.watch { [weak self] step in
            MainActor.assumeIsolated { self?.signInStep = step }
        })
    }

    /// Opens Headroom with its database in Application Support and the sign-ins in the Keychain.
    static func live() -> AppModel {
        let support = URL.applicationSupportDirectory.appending(path: "Headroom", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: support, withIntermediateDirectories: true)
        return AppModel(headroom: OpenHeadroomKt.openHeadroom(directory: support.path(percentEncoded: false),
                                                               secrets: KeychainStore()))
    }

    func provider(id: String) -> ProviderUi? {
        providers.first { $0.id == id }
    }

    func refresh(accountId: String? = nil) {
        headroom.refresh(accountId: accountId)
    }

    /// Syncs every account and returns when it is done.
    func refreshAll() async {
        try? await headroom.refreshAll()
    }

    func remove(accountId: String) {
        headroom.removeAccount(accountId: accountId)
    }

    func rename(accountId: String, nickname: String?) {
        headroom.renameAccount(accountId: accountId, nickname: nickname)
    }

    // MARK: Sign-in

    func startSignIn(providerId: String, accountId: String? = nil) {
        headroom.signIn.start(providerId: providerId, accountId: accountId)
    }

    func submitCode(_ code: String) {
        headroom.signIn.submitCode(code: code)
    }

    func submitApiKey(_ key: String) {
        headroom.signIn.submitApiKey(key: key)
    }

    func retrySignIn() {
        headroom.signIn.retry()
    }

    func cancelSignIn() {
        headroom.signIn.cancel()
    }
}
