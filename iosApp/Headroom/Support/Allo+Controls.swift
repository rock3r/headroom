import UIKit

extension Allo {
    @MainActor static func configureControls() {
        let bold = UIAccessibility.isBoldTextEnabled
        // Bars have fixed heights. Keep their standard sizes instead of scaling into adjacent controls.
        let title = font(.heading, size: 17, bold: bold)
        let largeTitle = font(.heading, size: 34, bold: bold)
        let button = font(.text, size: 17, weight: 600, bold: bold)
        let tab = font(.text, size: 11, weight: 600, bold: bold)
        let segment = font(.text, size: 13, weight: 500, bold: bold)
        let states: [UIControl.State] = [.normal, .highlighted, .selected, .disabled]

        func barButton(_ item: UIBarButtonItem) {
            for state in states {
                let attributes = (item.titleTextAttributes(for: state) ?? [:]).merging([.font: button]) { _, new in new }
                item.setTitleTextAttributes(attributes, for: state)
            }
        }
        func navigation(_ bar: UINavigationBar) {
            bar.titleTextAttributes = (bar.titleTextAttributes ?? [:]).merging([.font: title]) { _, new in new }
            bar.largeTitleTextAttributes = (bar.largeTitleTextAttributes ?? [:]).merging([.font: largeTitle]) { _, new in new }
        }
        func tabItem(_ item: UITabBarItem) {
            for state in states {
                let attributes = (item.titleTextAttributes(for: state) ?? [:]).merging([.font: tab]) { _, new in new }
                item.setTitleTextAttributes(attributes, for: state)
            }
        }
        func segmented(_ control: UISegmentedControl) {
            for state in states {
                let attributes = (control.titleTextAttributes(for: state) ?? [:]).merging([.font: segment]) { _, new in new }
                control.setTitleTextAttributes(attributes, for: state)
            }
        }

        navigation(UINavigationBar.appearance())
        tabItem(UITabBarItem.appearance())
        segmented(UISegmentedControl.appearance())
        barButton(UIBarButtonItem.appearance())

        // UIAppearance applies only to new controls; update visible ones when Bold Text changes too.
        var views: [UIView] = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
        while let view = views.popLast() {
            switch view {
            case let bar as UINavigationBar:
                navigation(bar)
                for item in bar.items ?? [] {
                    item.leftBarButtonItems?.forEach(barButton)
                    item.rightBarButtonItems?.forEach(barButton)
                    if let back = item.backBarButtonItem { barButton(back) }
                }
            case let bar as UITabBar: bar.items?.forEach(tabItem)
            case let control as UISegmentedControl: segmented(control)
            default: break
            }
            views.append(contentsOf: view.subviews)
        }
    }
}
