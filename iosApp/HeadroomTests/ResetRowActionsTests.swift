@testable import HeadroomApp
import HeadroomKit
import SwiftUI
import Testing
import UIKit

struct ResetRowActionsTests {
    @MainActor @Test func `available reset expiry ignores earlier unoffered pools`() {
        let offered = pool("ready", expiry: 200, offered: true)
        let queued = pool("queued", expiry: 100, offered: false)
        #expect(AvailableResetsRow.soonestExpiry(in: [queued, offered]) == 200)
        #expect(AvailableResetsRow.soonestExpiry(in: [queued]) == nil)
    }

    private func pool(_ status: String, expiry: Int64, offered: Bool) -> ResetPoolUi {
        ResetPoolUi(id: status, label: status, available: 1, status: status, scope: "codex", scopeWindows: [],
            soonestExpiryEpochSeconds: KotlinLong(value: expiry), canUseNow: offered, total: nil, expiryLines: [],
            anyTime: false, scopeWindowIds: [], clearsWindowIds: [], isOffered: offered)
    }

    @Test func `a separate sign-in is reachable without a usable pool`() {
        #expect(ResetRowActions.make(canAct: true, requiresSignIn: true, canRedeem: false,
            canAsk: false, hasResets: false) == [.signIn])
    }

    @Test func `asking for another card is reachable without a usable pool`() {
        #expect(ResetRowActions.make(canAct: true, requiresSignIn: false, canRedeem: true,
            canAsk: true, hasResets: false) == [.ask])
    }

    @Test func `demo and expired sign-ins cannot perform reset actions`() {
        #expect(ResetRowActions.make(canAct: false, requiresSignIn: true, canRedeem: true,
            canAsk: true, hasResets: true).isEmpty)
    }

    @MainActor @Test func `reset history stays visible when there are no upcoming resets`() throws {
        let history = ResetHistoryUi(
            accountId: "demo-claude", accountTitle: "Claude", providerId: "claude",
            windowLabel: "Weekly · all models", peaks: [82, 95, 100], current: 71,
            windowId: "seven_day", accountLabel: nil
        )
        let tab = ResetsTabUi(
            isDemo: false, alertsOn: 0, alertWindows: 0,
            withResets: [], upcoming: [], history: [history]
        )
        try withRenderedList(tab) { collection in
            try #require(collection.numberOfSections == 2)
            #expect(collection.numberOfItems(inSection: 0) == 2)
            #expect(collection.numberOfItems(inSection: 1) == 1)
            #expect(collection.cellForItem(at: IndexPath(item: 0, section: 1)) != nil)
        }
    }

    @MainActor @Test func `the history section is omitted when there is no history`() throws {
        let tab = ResetsTabUi(
            isDemo: false, alertsOn: 0, alertWindows: 0,
            withResets: [], upcoming: [], history: []
        )
        try withRenderedList(tab) { collection in
            #expect(collection.numberOfSections == 1)
            #expect(collection.numberOfItems(inSection: 0) == 2)
        }
    }

    @MainActor private func withRenderedList(
        _ tab: ResetsTabUi,
        check: (UICollectionView) throws -> Void
    ) throws {
        let directory = FileManager.default.temporaryDirectory
            .appending(path: "headroom-test-\(UUID().uuidString)", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let secrets = KeychainStore(service: "dev.sebastiano.headroom.tests.\(UUID().uuidString)")
        let headroom = OpenHeadroomKt.openHeadroom(
            directory: directory.path(percentEncoded: false),
            secrets: secrets
        )
        defer {
            headroom.close()
            try? FileManager.default.removeItem(at: directory)
        }
        let root = ResetsList(tab: tab, onUse: { _ in }, onAsk: { _ in })
            .environment(AppModel(headroom: headroom))
        let controller = UIHostingController(rootView: root)
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.rootViewController = controller
        window.makeKeyAndVisible()
        defer {
            window.isHidden = true
            window.rootViewController = nil
        }
        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        let collection = try #require(findCollectionView(in: controller.view))
        try check(collection)
    }

    @MainActor private func findCollectionView(in view: UIView) -> UICollectionView? {
        if let collection = view as? UICollectionView { return collection }
        for subview in view.subviews {
            if let found = findCollectionView(in: subview) { return found }
        }
        return nil
    }
}
