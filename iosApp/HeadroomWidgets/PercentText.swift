import Foundation

enum PercentText {
    /// "62% used" or "38% left".
    static func short(_ percent: Double, left: Bool) -> String {
        let value = Int(percent.rounded())
        return left ? String(localized: "\(value)% left") : String(localized: "\(value)% used")
    }
}
