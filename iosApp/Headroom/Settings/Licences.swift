import Foundation

/// The libraries HeadroomKit is built with, from the AboutLibraries export the build copies into
/// the app as `licences.json`.
struct Licences: Decodable {
    struct Library: Decodable, Identifiable {
        let uniqueId: String
        let name: String
        let artifactVersion: String?
        let description: String?
        let website: String?
        let licenses: [String]
        var id: String { uniqueId }
    }

    struct License: Decodable {
        let name: String
        let url: String?
        let content: String?
    }

    let libraries: [Library]
    let licenses: [String: License]

    static func load(bundle: Bundle = .main) -> Licences? {
        guard let url = bundle.url(forResource: "licences", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(Licences.self, from: data)
    }
}
