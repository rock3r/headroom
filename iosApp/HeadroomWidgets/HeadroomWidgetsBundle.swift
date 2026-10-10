import SwiftUI
import WidgetKit

/// Headroom's widgets: the usage and countdown widgets for the Home Screen and the Lock Screen, the
/// Control Center control, and the reset Live Activity.
@main
struct HeadroomWidgetsBundle: WidgetBundle {
    var body: some Widget {
        UsageWidget()
        CountdownWidget()
        NextResetControl()
        ResetActivityWidget()
    }
}
