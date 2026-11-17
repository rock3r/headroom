import CoreText
import SwiftUI
import UIKit

/// One rounded family for the app, widgets and native controls that expose font attributes.
enum Allo {
    enum Role {
        case text, heading, number, code

        var weight: CGFloat {
            switch self {
            case .text: 400
            case .heading, .code: 700
            case .number: 800
            }
        }
    }

    @MainActor static func configureControls() {
        UINavigationBar.appearance().largeTitleTextAttributes = [
            .font: UIFontMetrics(forTextStyle: .largeTitle).scaledFont(for: font(.heading, size: 34)),
        ]
        UINavigationBar.appearance().titleTextAttributes = [
            .font: UIFontMetrics(forTextStyle: .headline).scaledFont(for: font(.heading, size: 17)),
        ]
        for state: UIControl.State in [.normal, .highlighted, .selected, .disabled] {
            UIBarButtonItem.appearance().setTitleTextAttributes([
                .font: UIFontMetrics(forTextStyle: .body).scaledFont(for: font(.text, size: 17, weight: 600)),
            ], for: state)
            UITabBarItem.appearance().setTitleTextAttributes([
                .font: UIFontMetrics(forTextStyle: .caption2).scaledFont(for: font(.text, size: 11, weight: 600)),
            ], for: state)
            UISegmentedControl.appearance().setTitleTextAttributes([
                .font: UIFontMetrics(forTextStyle: .footnote).scaledFont(for: font(.text, size: 13, weight: 500)),
            ], for: state)
        }
    }

    static func font(_ role: Role, size: CGFloat, weight: CGFloat? = nil, tabular: Bool = false) -> UIFont {
        let variations: [NSNumber: CGFloat] = [
            0x77676874: weight ?? role.weight, // wght
            0x524F4E44: 100, // ROND
            0x6F70737A: min(32, max(9, size)), // opsz: text spacing at small sizes, display above 32pt
        ]
        var attributes: [CFString: Any] = [
            kCTFontNameAttribute: "AlloVariable-Regular",
            kCTFontVariationAttribute: variations,
        ]
        var features: [[CFString: Any]] = []
        if tabular || role == .number || role == .code {
            // Tabular figures keep a countdown or percentage from shifting as its digits change.
            features.append([
                kCTFontFeatureTypeIdentifierKey: kNumberSpacingType,
                kCTFontFeatureSelectorIdentifierKey: kMonospacedNumbersSelector,
            ])
        }
        if role == .code {
            // Allo's disambiguated I, l, 1 and 0 make device codes safe to transcribe.
            features.append([
                kCTFontOpenTypeFeatureTag: "ss02",
                kCTFontOpenTypeFeatureValue: 1,
            ])
        }
        attributes[kCTFontFeatureSettingsAttribute] = features
        let descriptor = CTFontDescriptorCreateWithAttributes(attributes as CFDictionary)
        return CTFontCreateWithFontDescriptor(descriptor, size, nil) as UIFont
    }
}

private struct AlloFont: ViewModifier {
    let role: Allo.Role
    let weight: CGFloat?
    let tabular: Bool
    @ScaledMetric private var size: CGFloat

    init(role: Allo.Role, size: CGFloat, relativeTo style: Font.TextStyle, weight: CGFloat? = nil, tabular: Bool = false) {
        self.role = role
        self.weight = weight
        self.tabular = tabular
        _size = ScaledMetric(wrappedValue: size, relativeTo: style)
    }

    func body(content: Content) -> some View {
        content.font(Font(Allo.font(role, size: size, weight: weight, tabular: tabular)))
    }
}

extension View {
    func alloFont(_ role: Allo.Role, size: CGFloat, relativeTo style: Font.TextStyle) -> some View {
        modifier(AlloFont(role: role, size: size, relativeTo: style))
    }

    /// Match the existing semantic sizes and Dynamic Type curves, without reverting to SF.
    func alloFont(_ style: Font.TextStyle, weight: CGFloat? = nil, tabular: Bool = false) -> some View {
        let size: CGFloat = switch style {
        case .largeTitle: 34
        case .title: 28
        case .title2: 22
        case .title3: 20
        case .headline, .body: 17
        case .callout: 16
        case .subheadline: 15
        case .footnote: 13
        case .caption: 12
        case .caption2: 11
        @unknown default: 17
        }
        return modifier(AlloFont(role: style == .headline ? .heading : .text, size: size,
                                 relativeTo: style, weight: weight, tabular: tabular))
    }
}
