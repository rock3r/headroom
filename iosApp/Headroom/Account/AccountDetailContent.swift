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
    @State private var connectingCursor = false
    @State private var redeeming: Redeeming?
    @State private var history = HistoryChartModel()

    /// What the redeem sheet opens for.
    struct Redeeming: Identifiable {
        let askForMore: Bool
        var id: Bool { askForMore }
    }

    var body: some View {
        if #available(iOS 26, *) {
            detail.navigationSubtitle(subtitle)
        } else {
            detail
        }
    }

    private var detail: some View {
        let orderedWindows = windows
        return ScrollViewReader { proxy in
            List {
                Section {
                    VStack(spacing: 10) {
                        HeroRing(account: account, session: session)
                        if let updated = account.updatedAtEpochSeconds?.int64Value {
                            Text("Updated \(Date(epochSeconds: updated), format: .relative(presentation: .named))")
                                .alloFont(.footnote)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .listRowSeparator(.hidden)
                    if account.signInExpired {
                        SignInExpiredNotice(
                            providerName: account.providerName,
                            providerId: account.providerId,
                            usesApiKey: account.usesApiKey,
                            authReason: account.authReason
                        ) { signingInAgain = true }
                    } else if let error = account.error {
                        Label(Texts.syncError(error), systemImage: "exclamationmark.triangle")
                            .foregroundStyle(.secondary)
                    }
                } header: {
                    if #unavailable(iOS 26) {
                        Text(subtitle)
                            .textCase(nil)
                    }
                }
                Section("Limits") {
                    ForEach(orderedWindows) { window in
                        DetailWindowRow(window: window, account: account)
                    }
                    if let balance = account.balance {
                        LabeledContent("Balance", value: Formats.balance(balance.amount, unit: balance.unit))
                    }
                }
                if let prompt = account.cursorPrompt {
                    Section("Cursor usage") {
                        if prompt == "readFailed" {
                            Text("Your Cursor sign-in is connected, but its usage could not be read this time.")
                                .foregroundStyle(.secondary)
                            Button("Retry Cursor usage", systemImage: "arrow.clockwise") {
                                model.refresh(accountId: account.id)
                            }
                            .disabled(isDemo || account.isRefreshing || account.signInExpired)
                        } else {
                            Text("Cursor serves its own model pools and the Grok Bot pool from a separate Cursor account. Connect it to see them here.")
                                .foregroundStyle(.secondary)
                            Button("Connect Cursor", systemImage: "person.badge.key") {
                                connectingCursor = true
                            }
                            .disabled(isDemo || account.signInExpired)
                        }
                    }
                }
                HistoryChartSection(account: account, history: history)
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
            .listSectionSpacing(.compact)
            // The history is watched while the screen shows, whether or not its section has rows.
            .onAppear { history.show(model.headroom, accountId: account.id) }
            .onChange(of: account.id) { _, id in history.show(model.headroom, accountId: id) }
            .onDisappear(perform: history.stop)
            .task(id: showsResets) {
                guard showsResets, account.resets != nil else { return }
                try? await Task.sleep(for: .milliseconds(300))
                withAnimation { proxy.scrollTo(Self.resetsAnchor, anchor: .top) }
            }
        }
        .toolbarTitleDisplayMode(.large)
        .navigationTitle(account.title)
        .sheet(item: $redeeming) { redeeming in
            RedeemSheet(account: account, askForMore: redeeming.askForMore)
        }
        .sheet(isPresented: $signingInAgain) {
            SignInView(mode: .again(accountId: account.id, providerId: account.providerId))
        }
        .sheet(isPresented: $connectingCursor) {
            CursorSignInSheet(account: account)
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
