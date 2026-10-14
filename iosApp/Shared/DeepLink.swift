import Foundation

/// The `headroom://` links the widgets and the control open, and the app follows.
enum DeepLink {
    static func account(_ id: String) -> URL {
        URL(string: "headroom://account/\(id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? id)")!
    }

    static func signIn(_ id: String) -> URL {
        URL(string: "headroom://signin/\(id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? id)")!
    }

    static let overview = URL(string: "headroom://overview")!
}
