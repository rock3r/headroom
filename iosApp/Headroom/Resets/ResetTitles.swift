/// Account names on the Resets tab.
enum ResetTitles {
    /// "Claude", or "Claude (sam@work)" when another account has the same name.
    static func name(_ title: String, label: String?) -> String {
        guard let label else { return title }
        return String(localized: "\(title) (\(label))")
    }
}
