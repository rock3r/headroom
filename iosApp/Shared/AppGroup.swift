import Foundation

/// The App Group the app and its widgets share, `group.` and the app's bundle id prefix. Both
/// Info.plists carry it, so changing the prefix for your own team changes it everywhere.
enum AppGroup {
    static let identifier = Bundle.main.object(forInfoDictionaryKey: "HeadroomAppGroup") as? String
        ?? "group.dev.sebastiano.headroom"

    static var container: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier)
    }
}
