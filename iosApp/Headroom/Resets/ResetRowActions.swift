/// The actions offered by both a reset's availability and the account's sign-in.
enum ResetRowActions: Equatable {
    case signIn, use, ask

    static func make(
        canAct: Bool, requiresSignIn: Bool, canRedeem: Bool, canAsk: Bool, hasResets: Bool
    ) -> [Self] {
        guard canAct else { return [] }
        if requiresSignIn { return [.signIn] }
        guard canRedeem else { return [] }
        return (hasResets ? [.use] : []) + (canAsk ? [.ask] : [])
    }
}
