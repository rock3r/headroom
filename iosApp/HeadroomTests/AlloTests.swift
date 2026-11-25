import CoreText
import HeadroomKit
import SwiftUI
import Testing
import UIKit
@testable import HeadroomApp

struct AlloTests {
    @MainActor @Test func `Bold Text changes the rendered type`() throws {
        func render(_ weight: LegibilityWeight) throws -> Data {
            let view = Text("Account settings").alloFont(.body)
                .environment(\.legibilityWeight, weight)
            return try #require(ImageRenderer(content: view).uiImage?.pngData())
        }
        #expect(try render(.regular) != render(.bold))
    }

    @MainActor @Test func `native bar fonts stay within their fixed control heights`() throws {
        defer { Allo.configureControls() }
        UITraitCollection(preferredContentSizeCategory: .accessibilityExtraExtraExtraLarge).performAsCurrent {
            Allo.configureControls()
        }
        let title = try #require(UINavigationBar.appearance().titleTextAttributes?[.font] as? UIFont)
        let tab = try #require(UITabBarItem.appearance().titleTextAttributes(for: .normal)?[.font] as? UIFont)
        #expect(title.pointSize == 17)
        #expect(tab.pointSize == 11)
    }

    @MainActor @Test(arguments: [DynamicTypeSize.xLarge, .xxLarge, .xxxLarge, .accessibility1, .accessibility5])
    func `allowance meters fit narrow cards with enlarged text`(typeSize: DynamicTypeSize) throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let headroom = OpenHeadroomKt.openHeadroom(directory: directory.path,
            secrets: KeychainStore(service: "dev.headroom.tests.\(UUID().uuidString)"))
        defer {
            headroom.close()
            try? FileManager.default.removeItem(at: directory)
        }
        let window = WindowUi(id: "weekly", label: "Premium requests", kind: "weekly", usedPercent: 40, leftPercent: 60,
            resetsAtEpochSeconds: nil, expiresAtEpochSeconds: nil, pace: nil, expectedPercent: nil,
            needsAttention: false, isInformational: false, isUnlimited: false, usedAmount: nil,
            limitAmount: nil, amountUnit: nil, canAlert: true, alertOn: false, isRecognised: true,
            paceChip: nil, pacePoints: 0, parts: [])
        let row = MeterRow(label: window.label, window: window, providerId: "copilot",
                          trailing: "Thu, Nov 19 at 9:42 PM", labelWidth: 96)
            .environment(AppModel(headroom: headroom))
            .dynamicTypeSize(typeSize)
        let host = UIHostingController(rootView: row)
        let size = host.sizeThatFits(in: CGSize(width: 280, height: 1000))
        #expect(size.width <= 280)
        #expect(size.height > 50)
        if !typeSize.isAccessibilitySize {
            let wideSize = host.sizeThatFits(in: CGSize(width: 1000, height: 1000))
            #expect(wideSize.height < 50)
        }
    }

    @Test func `a missing font licence does not hide library licences`() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).bundle")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let info = ["CFBundleIdentifier": "dev.headroom.tests.licences"]
        try PropertyListSerialization.data(fromPropertyList: info, format: .xml, options: 0)
            .write(to: directory.appendingPathComponent("Info.plist"))
        let source = try #require(Bundle.main.url(forResource: "licences", withExtension: "json"))
        try FileManager.default.copyItem(at: source, to: directory.appendingPathComponent("licences.json"))
        let bundle = try #require(Bundle(url: directory))
        let licences = try #require(Licences.load(bundle: bundle))
        #expect(!licences.libraries.isEmpty)
    }

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
        let version = try #require(CTFontCopyName(font, kCTFontVersionNameKey) as String?)
        #expect(version.hasPrefix("Version 1.100"))
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

    @Test func `display percentages use proportional digits`() {
        for size: CGFloat in [20, 30, 44] {
            for bold in [false, true] {
                let font = Allo.font(.number, size: size, bold: bold)
                let narrow = NSAttributedString(string: "11%", attributes: [.font: font]).size().width
                let wide = NSAttributedString(string: "88%", attributes: [.font: font]).size().width
                #expect(narrow < wide)
            }
        }
    }

    @MainActor @Test func `reset counts use proportional digits`() {
        func width(_ count: Int32) -> CGFloat {
            let resets = AccountResetsUi(availableNow: count, queued: 0, pools: [], canAskForMore: false,
                requiresSignIn: false, canRedeem: true, ineligibleReason: nil, showsSummary: true,
                holdsNone: false, notes: [], hasFooter: false)
            let view = UIHostingController(rootView: ResetCount(resets: resets).dynamicTypeSize(.large))
            return view.sizeThatFits(in: CGSize(width: 1000, height: 1000)).width
        }
        #expect(width(11) < width(88))
    }

    @Test func `codes keep equal width digits`() {
        for size: CGFloat in [20, 38, 72] {
            let font = Allo.font(.code, size: size)
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
        #expect(allo.artifactVersion == "1.1.0")
        let id = try #require(allo.licenses.first)
        let licence = try #require(licences.licenses[id])
        #expect(licence.content?.contains("Copyright 2026 Josh Puckett") == true)
        #expect(licence.content?.contains("The Inter Project Authors") == true)
    }
}
