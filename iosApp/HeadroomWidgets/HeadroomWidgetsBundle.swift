import SwiftUI
import WidgetKit

/// Headroom's widgets, as the Android ones: Rings, Bars, Shape and Countdown for the Home Screen,
/// the Lock Screen widget, the Control Center control, and the reset Live Activity.
@main
struct HeadroomWidgetsBundle: WidgetBundle {
    var body: some Widget {
        RingsWidget()
        BarsWidget()
        ShapeWidget()
        CountdownWidget()
        LockScreenWidget()
        NextResetControl()
        ResetActivityWidget()
    }
}
