import HeadroomKit
import SwiftUI

/// A window that resets soon, with its alert as a bell, as on Android.
struct UpcomingResetRow: View {
    let reset: UpcomingResetUi
    let canToggle: Bool
    @Environment(AppModel.self) private var model

    var body: some View {
        let cadence = String(localized: Texts.windowKindResource(reset.kind))
        let title = String(localized: "\(accountName) · \(cadence)")
        HStack(spacing: 12) {
            NavigationLink(value: AccountLink(accountId: reset.accountId)) {
                HStack(spacing: 12) {
                    ProviderAvatar(provider: model.provider(id: reset.providerId), size: 32)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title)
                            .alloFont(.subheadline, weight: 700)
                        if let name = windowName {
                            Text(name)
                                .alloFont(.subheadline)
                        }
                        TimelineView(.everyMinute) { context in
                            Text(when(now: context.date))
                                .alloFont(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(.rect)
            }
            .navigationLinkIndicatorVisibility(.hidden)
            .accessibilityHint("Open details")
            if canToggle {
                Button(action: toggle) { bell }
                .buttonStyle(.borderless)
                .accessibilityLabel("Reset alert for \(reset.accountTitle)")
                .accessibilityValue(reset.alertOn ? "On" : "Off")
            } else {
                bell
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(reset.alertOn
                                        ? "Demo reset alert for \(reset.accountTitle), on"
                                        : "Demo reset alert for \(reset.accountTitle), off")
                    .accessibilityAddTraits(.isStaticText)
            }
        }
        .listRowInsets(EdgeInsets(top: 5, leading: 16, bottom: 5, trailing: 16))
    }

    private var accountName: String {
        ResetTitles.name(reset.accountTitle, label: reset.accountLabel)
    }

    /// Some providers include the cadence in their window label, including legacy labels like "Weekly (Opus)".
    private var windowName: String? {
        let label = reset.windowLabel
        let cadence = reset.kind.capitalized
        guard label.hasPrefix(cadence) else { return label.isEmpty ? nil : label }
        let suffix = label.dropFirst(cadence.count)
        if suffix.isEmpty || suffix == " limit" { return nil }
        if suffix.hasPrefix(" · ") { return String(suffix.dropFirst(3)) }
        if suffix.hasPrefix(" ("), suffix.hasSuffix(")") {
            return String(suffix.dropFirst(2).dropLast())
        }
        if suffix.hasPrefix(" ") { return String(suffix.dropFirst()) }
        return label
    }

    private var bell: some View {
        Image(systemName: reset.alertOn ? "bell.fill" : "bell.slash")
            .foregroundStyle(reset.alertOn ? Color.accentColor : .secondary)
            .frame(width: canToggle ? 44 : 24, height: canToggle ? 44 : 24)
    }

    /// "Sun 09:00 · in 15h 28m".
    private func when(now: Date) -> String {
        let date = Date(epochSeconds: reset.resetsAtEpochSeconds)
        return String(localized: "\(Formats.short(date, now: now)) · in \(Formats.countdown(to: date, now: now))")
    }

    private func toggle() {
        model.headroom.accounts.setAlert(accountId: reset.accountId, windowId: reset.windowId, enabled: !reset.alertOn)
    }
}
