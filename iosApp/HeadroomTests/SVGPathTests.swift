@testable import HeadroomApp
import HeadroomKit
import SwiftUI
import Testing

struct SVGPathTests {
    @Test func `absolute lines and closing draw a square`() {
        let path = SVGPath.parse("M0 0 H10 V10 H0 Z")

        #expect(path.boundingRect == CGRect(x: 0, y: 0, width: 10, height: 10))
    }

    @Test func `relative commands move from the current point`() {
        let path = SVGPath.parse("m2 3 h4 v5 l-4 0 z")

        #expect(path.boundingRect == CGRect(x: 2, y: 3, width: 4, height: 5))
    }

    @Test func `numbers may run together as SVG allows`() {
        let path = SVGPath.parse("M1-1L.5.5L10,10")

        #expect(path.boundingRect == CGRect(x: 0.5, y: -1, width: 9.5, height: 11))
    }

    @Test func `pairs after a move are lines`() {
        let path = SVGPath.parse("M0 0 10 0 10 10")

        #expect(path.boundingRect == CGRect(x: 0, y: 0, width: 10, height: 10))
    }

    @Test func `a half circle arc reaches its far side`() {
        // From (0, 5) to (10, 5) with radius 5, sweeping through the top: y down to 0.
        let bounds = SVGPath.parse("M0 5 A5 5 0 0 1 10 5").boundingRect

        #expect(abs(bounds.minX - 0) < 0.01)
        #expect(abs(bounds.maxX - 10) < 0.01)
        #expect(abs(bounds.minY - 0) < 0.05)
        #expect(abs(bounds.maxY - 5) < 0.01)
    }

    @Test func `every provider logo draws inside its viewport`() throws {
        let providers = [
            ("claude", 40.0), ("codex", 40.0), ("copilot", 40.0), ("grok", 40.0),
            ("kimi", 40.0), ("zai", 40.0), ("opencode-go", 24.0), ("jetbrains", 24.0),
        ]
        let kit = ProvidersKt.allProviders()
        for (id, viewport) in providers {
            let provider = try #require(kit.first { $0.id == id })
            let bounds = SVGPath.parse(provider.logoPath).boundingRect

            #expect(!bounds.isEmpty, "\(id) draws nothing")
            #expect(bounds.minX >= -0.5 && bounds.maxX <= viewport + 0.5, "\(id) is \(bounds)")
            #expect(bounds.minY >= -0.5 && bounds.maxY <= viewport + 0.5, "\(id) is \(bounds)")
        }
    }
}
