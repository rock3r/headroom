import HeadroomKit
import SwiftUI

/// A window that resets soon, with its reset alert switch.
struct UpcomingResetRow: View {
    let reset: UpcomingResetUi
    let canToggle: Bool
    @Environment(AppModel.self) private var model

    var body: some View {
        HStack(spacing: 12) {
            NavigationLink(value: reset.accountId) {
                HStack(spacing: 12) {
                    ProviderAvatar(provider: model.provider(id: reset.providerId), size: 32)
                    VStack(alignment: .leading) {
                        Text("\(reset.accountTitle) · \(reset.windowLabel)")
                        Text(when)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            if canToggle {
                Toggle("Reset alert for \(reset.accountTitle)", isOn: alertBinding)
                    .labelsHidden()
            }
        }
    }

    private var when: String {
        let date = Date(epochSeconds: reset.resetsAtEpochSeconds)
        let left = ViewModelsKt.countdown(
            epochSeconds: reset.resetsAtEpochSeconds,
            nowEpochSeconds: Int64(Date.now.timeIntervalSince1970)
        )
        return String(localized: "\(date.formatted(.dateTime.weekday().hour().minute())) · in \(left)")
    }

    private var alertBinding: Binding<Bool> {
        Binding {
            reset.alertOn
        } set: { on in
            model.headroom.accounts.setAlert(accountId: reset.accountId, windowId: reset.windowId, enabled: on)
        }
    }
}
