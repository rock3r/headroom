/// ZCode credentials are separate from Z.AI's API key; other providers renew the account sign-in.
enum ResetReauthentication {
    case zCode, account

    static func target(providerId: String, result: String) -> Self? {
        guard result == "signInAgain" else { return nil }
        return providerId == "zai" ? .zCode : .account
    }
}
