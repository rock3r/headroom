import HeadroomKit
import SwiftUI

/// Every window of an account, and what the user can do with it, as the Android detail.
struct AccountDetailContent: View {
    let account: AccountUi
    /// A demo account can be looked at, but there is nothing to update, rename or remove.
    let isDemo: Bool
    /// Scrolls to the resets card, as when a reset reminder opened the account.
    var showsResets = false
    @Environment(AppModel.self) private var model
    @State private var signingInAgain = false
    @State private var redeeming: Redeeming?

    /// What the redeem sheet opens for.
    struct Redeeming: Identifiable {
        let askForMore: Bool
        var id: Bool { askForMore }
    }

    var body: some View {
        ScrollViewReader { proxy in
            List {
                Section {
                    HeroRing(account: account, session: session)
                        .padding(.vertical, 8)
                    if account.signInExpired {
                        SignInExpiredNotice(providerName: account.providerName) { signingInAgain = true }
                    } else if let error = account.error {
                        Label(Texts.syncError(error), systemImage: "exclamationmark.triangle")
                            .foregroundStyle(.secondary)
                    }
                    if let updated = account.updatedAtEpochSeconds?.int64Value {
                        Text("Updated \(Date(epochSeconds: updated), format: .relative(presentation: .named))")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                Section("Limits") {
                    ForEach(windows) { window in
                        DetailWindowRow(window: window, account: account)
                    }
                    if let balance = account.balance {
                        LabeledContent("Balance", value: Formats.balance(balance.amount, unit: balance.unit))
                    }
                }
                PaceChartSection(account: account)
                if let resets = account.resets {
                    ResetsCard(
                        account: account,
                        resets: resets,
                        canAct: !isDemo && !account.signInExpired,
                        onUse: { redeeming = Redeeming(askForMore: false) },
                        onAsk: { redeeming = Redeeming(askForMore: true) }
                    )
                    .id(Self.resetsAnchor)
                }
                if !isDemo {
                    ResetAlertsSection(account: account)
                    AccountActions(account: account)
                }
            }
            .task(id: showsResets) {
                guard showsResets, account.resets != nil else { return }
                try? await Task.sleep(for: .milliseconds(300))
                withAnimation { proxy.scrollTo(Self.resetsAnchor, anchor: .top) }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack(spacing: 0) {
                    Text(account.title)
                        .font(.headline)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .accessibilityElement(children: .combine)
            }
        }
        .navigationTitle(account.title)
        .sheet(item: $redeeming) { redeeming in
            RedeemSheet(account: account, askForMore: redeeming.askForMore)
        }
        .sheet(isPresented: $signingInAgain) {
            SignInView(mode: .again(accountId: account.id, providerId: account.providerId))
        }
    }

    private static let resetsAnchor = "resets"

    /// "Max 20x · sam@example.com", or the label alone.
    private var subtitle: String {
        if let plan = account.plan, plan != account.title { return "\(plan) · \(account.label)" }
        return account.label
    }

    /// The windows the Android detail lists: unlimited ones left out, by kind.
    private var windows: [WindowUi] {
        let order = ["session", "daily", "weekly", "monthly", "other", "credit"]
        return account.windows.filter { !$0.isUnlimited }
            .enumerated()
            .sorted { lhs, rhs in
                let left = order.firstIndex(of: lhs.element.kind) ?? order.count
                let right = order.firstIndex(of: rhs.element.kind) ?? order.count
                return left == right ? lhs.offset < rhs.offset : left < right
            }
            .map(\.element)
    }

    private var session: WindowUi? {
        account.windows.first { $0.kind == "session" && !$0.isInformational && $0.id != account.primary?.id }
    }
}
