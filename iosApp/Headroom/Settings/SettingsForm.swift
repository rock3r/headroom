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
                    .alloFont(.footnote)
            }
            Section("Appearance") {
                NavigationLink {
                    themeSettings
                } label: {
                    LabeledContent("Theme", value: String(localized: themeName(settings.theme)))
                }
                NavigationLink {
                    paletteSettings
                } label: {
                    LabeledContent {
                        Text((Appearance.palettes.first { $0.id == settings.palette } ?? Appearance.palettes[0]).name)
                    } label: {
                        Text("Colours")
                    }
                }
                Toggle(isOn: Binding(get: { settings.reduceMotion }, set: { settingsApi.setReduceMotion(enabled: $0) })) {
                    Text("Reduce motion")
                    Text("Short fades instead of moving transitions, and no looping animations. Always on when animations are off on your device.")
                        .alloFont(.subheadline)
                }
            }
            DelightsSettings(settings: settings)
            Section("Resets") {
                switchRow("resetExpiryReminders", "Remind me before a reset expires", settings.resetExpiryReminders,
                          body: "A notification about a day before a reset you can use expires. At most one a day, for every reset that expires soon.")
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
                    .alloFont(.footnote)
            }
            Section("Usage history") {
                switchRow("keepLongHistory", "Keep long-term history", settings.keepLongHistory,
                          body: "Keeps a year of usage for the charts, in less detail as it gets older. Turn off to keep only the last 60 days.")
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
                    .alloFont(.footnote)
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
                            .alloFont(.footnote)
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

    private var themeSettings: some View {
        List(["system", "light", "dark"], id: \.self) { theme in
            let selected = theme == model.settings?.theme
            Button {
                settingsApi.setTheme(id: theme)
            } label: {
                HStack {
                    Text(themeName(theme))
                    Spacer()
                    if selected {
                        Image(systemName: "checkmark")
                            .foregroundStyle(.tint)
                            .accessibilityHidden(true)
                    }
                }
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(selected ? .isSelected : [])
        }
        .navigationTitle("Theme")
    }

    private func themeName(_ id: String) -> LocalizedStringResource {
        switch id {
        case "light": "Light"
        case "dark": "Dark"
        default: "System"
        }
    }

    private var paletteSettings: some View {
        List(Appearance.palettes) { palette in
            let selected = palette.id == model.settings?.palette
            Button {
                settingsApi.setPalette(id: palette.id)
            } label: {
                HStack(spacing: 12) {
                    if let tint = Appearance.tint(palette.id) {
                        Circle().fill(tint).frame(width: 14, height: 14)
                    } else {
                        Image(systemName: "circle.lefthalf.filled")
                            .font(.system(size: 14))
                            .frame(width: 14, height: 14)
                            .foregroundStyle(.secondary)
                    }
                    Text(palette.name)
                    Spacer()
                    if selected {
                        Image(systemName: "checkmark")
                            .foregroundStyle(.tint)
                    }
                }
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(palette.name))
            .accessibilityAddTraits(selected ? .isSelected : [])
        }
        .navigationTitle("Colours")
    }

    private func binding(_ value: String, _ set: @escaping @MainActor (String) -> Void) -> Binding<String> {
        // A closure rather than `set` itself: Swift 6.3 (Xcode 26) crashes converting the function.
        Binding(get: { value }, set: { newValue in set(newValue) })
    }

    private func switchRow(_ id: String, _ title: LocalizedStringKey, _ isOn: Bool, body: LocalizedStringKey) -> some View {
        Toggle(isOn: Binding(get: { isOn }, set: { settingsApi.setSwitch(id: id, enabled: $0) })) {
            Text(title)
            Text(body)
                .alloFont(.subheadline)
        }
    }

    private static var version: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? ""
        let build = info?["CFBundleVersion"] as? String ?? ""
        return "\(version) (\(build))"
    }
}
