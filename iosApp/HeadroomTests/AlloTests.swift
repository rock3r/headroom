import CoreText
import SwiftUI
import Testing
import UIKit
@testable import HeadroomApp

struct AlloTests {
    @Test func `the widget extension bundles and registers Allo`() throws {
        let plugins = try #require(Bundle.main.builtInPlugInsURL)
        let widgets = try #require(Bundle(url: plugins.appendingPathComponent("HeadroomWidgets.appex")))
        let registered = try #require(widgets.object(forInfoDictionaryKey: "UIAppFonts") as? [String])
        #expect(registered.contains("AlloVariable.ttf"))
        let fontURL = try #require(widgets.url(forResource: "AlloVariable", withExtension: "ttf"))
        let descriptors = try #require(CTFontManagerCreateFontDescriptorsFromURL(fontURL as CFURL) as? [CTFontDescriptor])
        let names = descriptors.map { CTFontCopyPostScriptName(CTFontCreateWithFontDescriptor($0, 17, nil)) as String }
        #expect(names.contains("AlloVariable-Regular"))
    }

    @Test func `the bundled font has all three variable axes`() throws {
        let font = try #require(UIFont(name: "AlloVariable-Regular", size: 17))
        let axes = try #require(CTFontCopyVariationAxes(font) as? [[String: Any]])
        let names = Set(axes.compactMap { $0[kCTFontVariationAxisNameKey as String] as? String })
        #expect(names == ["Optical size", "Weight", "Roundness"])
    }

    @Test func `every role uses the fully rounded design`() throws {
        for role: Allo.Role in [.text, .heading, .number, .code] {
            let font = Allo.font(role, size: 17)
            #expect(font.familyName == "Allo Variable")
            let axes = try #require(CTFontCopyVariation(font) as? [NSNumber: CGFloat])
            #expect(axes[0x524F4E44] == 100)
        }
    }

    @MainActor @Test func `body and caption text grow with Dynamic Type`() {
        for style: Font.TextStyle in [.body, .caption] {
            let regular = UIHostingController(rootView: Text("Account settings").alloFont(style).dynamicTypeSize(.large))
            let larger = UIHostingController(rootView: Text("Account settings").alloFont(style).dynamicTypeSize(.xxxLarge))
            let bounds = CGSize(width: 1000, height: 1000)
            let smallSize = regular.sizeThatFits(in: bounds)
            let largeSize = larger.sizeThatFits(in: bounds)
            #expect(largeSize.width > smallSize.width)
            #expect(largeSize.height > smallSize.height)
        }
    }

    @Test func `changing quota digits does not change their width`() {
        for size: CGFloat in [20, 38, 72] {
            let font = Allo.font(.number, size: size)
            #expect(font.familyName == "Allo Variable")
            let widths = (0 ... 9).map {
                NSAttributedString(string: "\($0)", attributes: [.font: font]).size().width
            }
            #expect(widths.max()! - widths.min()! < 0.01)
        }
    }

    @Test func `Allo attribution ships alongside the library licences`() throws {
        let licences = try #require(Licences.load())
        let allo = try #require(licences.libraries.first { $0.name == "Allo" })
        let id = try #require(allo.licenses.first)
        let licence = try #require(licences.licenses[id])
        #expect(licence.content?.contains("Copyright 2026 Josh Puckett") == true)
        #expect(licence.content?.contains("The Inter Project Authors") == true)
    }
}
