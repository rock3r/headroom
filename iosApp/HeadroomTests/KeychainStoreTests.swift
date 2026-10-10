@testable import HeadroomApp
import Foundation
import Testing

/// The Keychain behind the sign-ins. Each test uses a service of its own, so nothing real is touched.
struct KeychainStoreTests {
    private let store = KeychainStore(service: "dev.sebastiano.headroom.tests.\(UUID().uuidString)")

    @Test func `a value written can be read back and replaced`() {
        #expect(store.write(key: "credential.a", value: "first"))
        #expect(store.read(key: "credential.a") == "first")

        #expect(store.write(key: "credential.a", value: "second"))
        #expect(store.read(key: "credential.a") == "second")
        #expect(store.delete(key: "credential.a"))
    }

    @Test func `a deleted or missing value reads as nothing`() {
        #expect(store.read(key: "missing") == nil)
        #expect(store.delete(key: "missing"))

        #expect(store.write(key: "credential.b", value: "value"))
        #expect(store.delete(key: "credential.b"))
        #expect(store.read(key: "credential.b") == nil)
    }
}
