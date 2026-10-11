import HeadroomKit
import SwiftUI

/// A window that resets soon, with its alert as a bell, as on Android.
struct UpcomingResetRow: View {
    let reset: UpcomingResetUi
    let canToggle: Bool
    @Environment(AppModel.self) private var model

    var body: some View {
        HStack(spacing: 12) {
            NavigationLink(value: AccountLink(accountId: reset.accountId)) {
                HStack(spacing: 12) {
                    ProviderAvatar(provider: model.provider(id: reset.providerId), size: 32)
                    VStack(alignment: .leading) {
                        Text("\(ResetTitles.name(reset.accountTitle, label: reset.accountLabel)) · \(reset.windowLabel)")
                        TimelineView(.everyMinute) { context in
                            Text(when(now: context.date))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .accessibilityHint("Open details")
            if canToggle {
                Button(action: toggle) {
                    Image(systemName: reset.alertOn ? "bell.fill" : "bell.slash")
                        .foregroundStyle(reset.alertOn ? Color.accentColor : .secondary)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Reset alert for \(reset.accountTitle)")
                .accessibilityValue(reset.alertOn ? "On" : "Off")
            }
        }
    }

    /// "Sun, Oct 11 at 09:00 · in 15h 28m".
    private func when(now: Date) -> String {
        let date = Date(epochSeconds: reset.resetsAtEpochSeconds)
        return String(localized: "\(Formats.long(date)) · in \(Formats.countdown(to: date, now: now))")
    }

    private func toggle() {
        model.headroom.accounts.setAlert(accountId: reset.accountId, windowId: reset.windowId, enabled: !reset.alertOn)
    }
}
