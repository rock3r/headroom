import HeadroomKit
import SwiftUI

/// The settings, each change saved by HeadroomKit at once.
struct SettingsForm: View {
    let settings: SettingsUi
    @Environment(AppModel.self) private var model

    var body: some View {
        Form {
            Section("Accounts") {
                NavigationLink {
                    AccountsSettings()
                } label: {
                    LabeledContent("Your accounts") {
                        if let overview = model.overview, !overview.isDemo {
                            Text("\(overview.accounts.count)")
                        }
                    }
                }
            }
            Section {
                Picker("Show quotas as", selection: binding(settings.quotaDisplay) { settingsApi.setQuotaDisplay(id: $0) }) {
                    Text("Used").tag("used")
                    Text("Left").tag("left")
                }
            } header: {
                Text("Display")
            } footer: {
                Text(settings.quotaDisplay == "left"
                    ? "Numbers, rings and bars show how much of each limit you have left."
                    : "Numbers, rings and bars show how much of each limit you have used.")
            }
            Section {
                Picker("Theme", selection: binding(settings.theme) { settingsApi.setTheme(id: $0) }) {
                    Text("System").tag("system")
                    Text("Light").tag("light")
                    Text("Dark").tag("dark")
                }
                Picker("Colours", selection: binding(settings.palette) { settingsApi.setPalette(id: $0) }) {
                    ForEach(Appearance.palettes) { palette in
                        Label {
                            Text(palette.name)
                        } icon: {
                            Image(systemName: "circle.fill")
                                .foregroundStyle(Appearance.tint(palette.id) ?? .accentColor)
                        }
                        .tag(palette.id)
                    }
                }
                Toggle("Reduce motion", isOn: Binding(get: { settings.reduceMotion }, set: { settingsApi.setReduceMotion(enabled: $0) }))
            } header: {
                Text("Appearance")
            } footer: {
                Text("Turns Headroom's own animations off. Always on when Reduce Motion is on in your device's Accessibility settings.")
            }
            Section {
                switchRow("resetExpiryReminders", "Remind me before a reset expires", settings.resetExpiryReminders,
                          body: "A notification about a day before a reset you can use expires. At most one a day, for every reset that expires soon.")
                switchRow("redeemClaudeResets", "Redeem Claude resets (experimental)", settings.redeemClaudeResets,
                          body: "Use Claude's saved resets from Headroom. Claude has not published this API, so it may fail or stop working.")
                switchRow("resetIsland", "Reset Live Activity", settings.resetIsland,
                          body: "When a limit resets within a few hours, its countdown shows on the Lock Screen and in the Dynamic Island.")
            } header: {
                Text("Resets")
            }
            Section {
                Picker("Update", selection: binding(settings.syncFrequency) { settingsApi.setSyncFrequency(id: $0) }) {
                    Text("Every 15 minutes").tag("minutes15")
                    Text("Every 30 minutes").tag("minutes30")
                    Text("Every hour").tag("hour1")
                    Text("Every 3 hours").tag("hours3")
                    Text("Every 6 hours").tag("hours6")
                    Text("Only when I open the app").tag("onOpen")
                }
            } header: {
                Text("Background updates")
            } footer: {
                Text("iOS decides when background updates run, no sooner than this. Reset alerts arrive on time whatever you choose here.")
            }
            Section {
                Text("Touch and hold your Home Screen or Lock Screen, tap Edit, then Add Widget, and choose Headroom. The Next reset control is in Control Center's gallery.")
                    .foregroundStyle(.secondary)
            } header: {
                Text("Widgets")
            }
            Section("About") {
                LabeledContent("Version", value: Self.version)
            }
        }
    }

    private var settingsApi: HeadroomSettings {
        model.headroom.settings
    }

    private func binding(_ value: String, _ set: @escaping @MainActor (String) -> Void) -> Binding<String> {
        Binding(get: { value }, set: set)
    }

    private func switchRow(_ id: String, _ title: LocalizedStringKey, _ isOn: Bool, body: LocalizedStringKey) -> some View {
        Toggle(isOn: Binding(get: { isOn }, set: { settingsApi.setSwitch(id: id, enabled: $0) })) {
            Text(title)
            Text(body)
        }
    }

    private static var version: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? ""
        let build = info?["CFBundleVersion"] as? String ?? ""
        return "\(version) (\(build))"
    }
}
