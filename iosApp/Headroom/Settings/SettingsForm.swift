import HeadroomKit
import SwiftUI
import WidgetKit

/// The settings, in the Android order, each change saved by HeadroomKit at once.
struct SettingsForm: View {
    let settings: SettingsUi
    @Environment(AppModel.self) private var model
    @State private var controlMode = ControlSettings.mode

    var body: some View {
        Form {
            Text("Accounts, display and updates")
                .foregroundStyle(.secondary)
                .listRowBackground(Color.clear)
            Section("Accounts") {
                NavigationLink {
                    AccountsSettings()
                } label: {
                    AccountsRow()
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
            Section("Appearance") {
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
                                .accessibilityHidden(true)
                        }
                        .tag(palette.id)
                    }
                }
                Toggle(isOn: Binding(get: { settings.reduceMotion }, set: { settingsApi.setReduceMotion(enabled: $0) })) {
                    Text("Reduce motion")
                    Text("Short fades instead of moving transitions, and no looping animations. Always on when animations are off on your device.")
                }
            }
            DelightsSettings(settings: settings)
            Section("Resets") {
                switchRow("resetExpiryReminders", "Remind me before a reset expires", settings.resetExpiryReminders,
                          body: "A notification about a day before a reset you can use expires. At most one a day, for every reset that expires soon.")
                switchRow("redeemClaudeResets", "Redeem Claude resets (experimental)", settings.redeemClaudeResets,
                          body: "Use Claude's saved resets from Headroom. Claude has not published this API, so it may fail or stop working.")
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
            WidgetsSettings()
            Section {
                Picker("Control Center", selection: $controlMode) {
                    VStack(alignment: .leading) {
                        Text("Show the next reset")
                        Text("For example, \"Claude · in 2d 4h\"")
                    }
                    .tag(ControlSettings.nextReset)
                    VStack(alignment: .leading) {
                        Text("Show the tightest quota")
                        Text("For example, \"Grok · 88% used\"")
                    }
                    .tag(ControlSettings.tightest)
                }
                .pickerStyle(.inline)
                .labelsHidden()
            } header: {
                Text("Control Center")
            } footer: {
                Text("Add the Headroom control from Control Center's gallery: touch and hold an empty space in Control Center, then tap Add a Control.")
            }
            .onChange(of: controlMode) { _, mode in
                ControlSettings.mode = mode
                ControlCenter.shared.reloadAllControls()
            }
            Section("About") {
                NavigationLink {
                    LicencesView()
                } label: {
                    VStack(alignment: .leading) {
                        Text("Open-source licences")
                        Text("The libraries Headroom is built with")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                Text("Version \(Self.version)")
                    .foregroundStyle(.secondary)
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
