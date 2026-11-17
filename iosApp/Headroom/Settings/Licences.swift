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
              let data = try? Data(contentsOf: url),
              let libraries = try? JSONDecoder().decode(Licences.self, from: data) else { return nil }
        guard let fontURL = bundle.url(forResource: "Allo-OFL", withExtension: "txt"),
              let fontLicence = try? String(contentsOf: fontURL, encoding: .utf8) else { return libraries }
        let allo = Library(
            uniqueId: "allo", name: "Allo", artifactVersion: "1.0",
            description: "Typeface by Josh Puckett, built on Inter by Rasmus Andersson.",
            website: "https://github.com/joshpuckett/allo", licenses: ["Allo-OFL"]
        )
        var licenses = libraries.licenses
        licenses["Allo-OFL"] = License(
            name: "SIL Open Font License 1.1", url: "https://openfontlicense.org", content: fontLicence
        )
        return Licences(libraries: libraries.libraries + [allo], licenses: licenses)
    }
}
