import Foundation

extension DeepLink {
    /// Where a `headroom://` link leads in the app, or nil for the overview as it is.
    static func route(_ url: URL) -> AppModel.Route? {
        let parts = url.pathComponents.filter { $0 != "/" }
        switch (url.host(), parts.first, parts.dropFirst().first) {
        case ("account", let id?, "resets"): return .accountResets(id)
        case ("account", let id?, _): return .account(id)
        case ("signin", let id?, _): return .signIn(accountId: id)
        case ("resets", _, _): return .resetsTab
        case ("stats", _, _): return .statsTab
        default: return nil
        }
    }
}
