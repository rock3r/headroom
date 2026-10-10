import HeadroomKit

/// What the services follow: they update whenever the overview or the settings change.
struct ServicesInput: Equatable {
    let overview: OverviewUi?
    let settings: SettingsUi?
}
