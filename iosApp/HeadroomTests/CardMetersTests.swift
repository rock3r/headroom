@testable import HeadroomApp
import HeadroomKit
import Testing

struct CardMetersTests {
    private func window(
        id: String,
        label: String,
        kind: String,
        usedPercent: Double = 40,
        resetsAt: Int64? = 2_000_000_000
    ) -> WindowUi {
        WindowUi(
            id: id, label: label, kind: kind, usedPercent: usedPercent, leftPercent: 100 - usedPercent,
            resetsAtEpochSeconds: resetsAt.map(KotlinLong.init(value:)), expiresAtEpochSeconds: nil,
            pace: nil, expectedPercent: nil, needsAttention: false, isInformational: false,
            isUnlimited: false, usedAmount: nil, limitAmount: nil, amountUnit: nil,
            canAlert: kind == "weekly" || kind == "monthly", alertOn: false,
            isRecognised: true, paceChip: nil, pacePoints: 0, parts: []
        )
    }

    private func account(
        providerId: String,
        primary: WindowUi?,
        monthly: WindowUi? = nil,
        windows: [WindowUi],
        separateAllowances: Bool = false
    ) -> AccountUi {
        AccountUi(
            id: providerId, providerId: providerId, providerName: providerId, title: providerId,
            label: "sam@example.com", plan: nil, primary: primary, windows: windows,
            isRefreshing: false, signInExpired: false, error: nil, updatedAtEpochSeconds: nil,
            needsAttention: false, resets: nil, balance: nil,
            separateAllowances: separateAllowances, justReset: false,
            usesApiKey: false, authReason: nil, cursorPrompt: nil, monthly: monthly
        )
    }

    @MainActor @Test func `OpenCode Go includes weekly, monthly and session bars`() {
        let weekly = window(id: "weekly", label: "Weekly", kind: "weekly", resetsAt: 2_000_000_000)
        let monthly = window(id: "monthly", label: "Monthly", kind: "monthly", resetsAt: 2_001_000_000)
        let session = window(id: "rolling", label: "5 hour", kind: "session", resetsAt: 2_000_010_000)
        let acc = account(providerId: "opencode-go", primary: weekly, monthly: monthly,
                          windows: [weekly, session, monthly])

        let meters = CardMeters(account: acc)

        #expect(meters.primaryRows.map(\.id) == ["weekly", "monthly"])
        #expect(meters.sessionWindow?.id == "rolling")
        #expect(meters.resetLabels.count == 3)
    }

    @MainActor @Test func `monthly-only accounts show only the primary row`() {
        let monthly = window(id: "premium_interactions", label: "AI Credits", kind: "monthly")
        let acc = account(providerId: "copilot", primary: monthly, monthly: nil, windows: [monthly])

        let meters = CardMeters(account: acc)

        #expect(meters.primaryRows.map(\.id) == ["premium_interactions"])
        #expect(meters.sessionWindow == nil)
    }

    @MainActor @Test func `separate allowances list each allowance without duplicating monthly`() {
        let weekly = window(id: "jb:combined", label: "Combined", kind: "weekly")
        let monthly = window(id: "jb:license:a", label: "AI Pro", kind: "monthly")
        let acc = account(providerId: "jetbrains", primary: weekly, monthly: nil,
                          windows: [weekly, monthly], separateAllowances: true)

        let meters = CardMeters(account: acc)

        #expect(meters.primaryRows.map(\.id) == ["jb:combined", "jb:license:a"])
    }

    @MainActor @Test func `accounts without a primary window have no primary rows`() {
        let acc = account(providerId: "opencode-go", primary: nil, monthly: nil, windows: [])

        let meters = CardMeters(account: acc)

        #expect(meters.primaryRows.isEmpty)
        #expect(meters.sessionWindow == nil)
    }

    @MainActor @Test func `meter row accessibility label speaks the usage share and reset`() {
        let monthly = window(id: "monthly", label: "Monthly", kind: "monthly", usedPercent: 48)

        #expect(MeterRow.accessibilityLabel(label: "Monthly", window: monthly, trailing: "Dec 11", showsLeft: false)
            == "Monthly, 48% used, Dec 11")
        #expect(MeterRow.accessibilityLabel(label: "Monthly", window: monthly, trailing: "Dec 11", showsLeft: true)
            == "Monthly, 52% left, Dec 11")
        #expect(MeterRow.accessibilityLabel(label: "Monthly", window: monthly, trailing: nil, showsLeft: false)
            == "Monthly, 48% used")
    }
}
