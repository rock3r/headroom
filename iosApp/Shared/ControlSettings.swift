import Foundation

/// What the Control Center control shows, as the Android Quick Settings tile's setting: the next
/// reset, or the tightest quota. Kept in the App Group, where the control reads it.
enum ControlSettings {
    static let nextReset = "nextReset"
    static let tightest = "tightest"
    private static let key = "control.subtitle"

    static var mode: String {
        get { UserDefaults(suiteName: AppGroup.identifier)?.string(forKey: key) ?? nextReset }
        set { UserDefaults(suiteName: AppGroup.identifier)?.set(newValue, forKey: key) }
    }
}
