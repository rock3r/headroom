import HeadroomKit
import SwiftUI

/// Using one of an account's usage-limit resets, or asking its provider for another. The steps are
/// HeadroomKit's, the same as on Android, so no path can use two resets for one try. The bars on
/// top show what a reset clears, and refill when it worked.
struct RedeemSheet: View {
    let account: AccountUi
    let askForMore: Bool
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    /// The pool the sheet is working with, kept once the step no longer names it.
    @State private var pool: ResetPoolUi?
    /// The reset worked: confetti over the bars, then a gloss once the new usage refills them.
    @State private var celebrating = false
    @State private var glossing = false
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    RefillBars(account: current, cleared: Set(pool?.clearsWindowIds ?? []), working: isBusy || glossing)
                        .padding(.top, 8)
                        .overlay {
                            if celebrating {
                                GeometryReader { proxy in
                                    ConfettiView(origin: CGPoint(x: proxy.size.width / 2, y: 0),
                                                 colors: confettiColors) { celebrating = false }
                                }
                            }
                        }
                    RedeemStepView(step: model.redeemStep, account: current, onClose: close)
                }
                .padding()
            }
            .toolbar {
                ToolbarItem(placement: .principal) {
                    HStack(spacing: 8) {
                        ProviderAvatar(provider: model.provider(id: account.providerId), size: 26)
                        Text("Resets")
                            .font(.headline)
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Resets for \(account.title)")
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close", action: close)
                        .disabled(isBusy)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
        }
        // The provider is working: closing now would leave the user not knowing whether it worked.
        .interactiveDismissDisabled(isBusy)
        .onAppear(perform: start)
        .onDisappear(perform: stop)
        .onChange(of: stepPool?.id) { _, _ in
            if let stepPool { pool = stepPool }
        }
        .onChange(of: succeeded) { _, succeeded in
            guard succeeded else { return }
            // The overview does not play this reset again when the new usage arrives.
            model.delights.claim(accountId: account.id)
            if model.settings?.resetConfetti != false,
               !DelightCenter.motionReduced(model.settings?.reduceMotion ?? false) {
                celebrating = true
            }
        }
        .onChange(of: current.updatedAtEpochSeconds?.int64Value) { _, _ in
            guard succeeded, model.settings?.resetConfetti != false,
                  !DelightCenter.motionReduced(model.settings?.reduceMotion ?? false) else { return }
            glossing = true
            Task {
                try? await Task.sleep(for: .seconds(1.4))
                glossing = false
            }
        }
    }

    /// The account as it is now, so the bars move when the reset lands.
    private var current: AccountUi {
        model.account(id: account.id) ?? account
    }

    private var stepPool: ResetPoolUi? {
        switch model.redeemStep {
        case let step as RedeemUiConfirm: step.pool
        case let step as RedeemUiResetting: step.pool
        case let step as RedeemUiChecking: step.pool
        default: nil
        }
    }

    private var succeeded: Bool {
        (model.redeemStep as? RedeemUiFinished)?.outcome == "success"
    }

    private var confettiColors: [Color] {
        let accent = ProviderColors(providerId: account.providerId, colorScheme: colorScheme).accent
        return [accent, accent, .accentColor, .orange]
    }

    private var isBusy: Bool {
        let step = model.redeemStep
        return step is RedeemUiResetting || step is RedeemUiChecking || step is RedeemUiAsking
    }

    private func start() {
        model.headroom.resets.redeem.start(accountId: account.id, askForMore: askForMore)
    }

    private func close() {
        dismiss()
    }

    private func stop() {
        model.headroom.resets.redeem.close()
        model.headroom.resets.zCode.cancel()
    }
}
