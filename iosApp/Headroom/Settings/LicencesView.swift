import SwiftUI

/// "Open-source licences": every library HeadroomKit is built with, and its licence.
struct LicencesView: View {
    @State private var licences = Licences.load()

    var body: some View {
        List {
            if let licences {
                ForEach(licences.libraries.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }) { library in
                    NavigationLink {
                        LicenceDetail(library: library, licences: licences)
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(library.name)
                            Text([library.artifactVersion, library.licenses.compactMap { licences.licenses[$0]?.name }.first]
                                .compactMap { $0 }.joined(separator: " · "))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            } else {
                ContentUnavailableView("No licences", systemImage: "doc.text")
            }
        }
        .navigationTitle("Open-source licences")
    }
}

/// One library: what it is, where it lives, and the full text of its licence.
private struct LicenceDetail: View {
    let library: Licences.Library
    let licences: Licences

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let description = library.description, !description.isEmpty {
                    Text(description)
                }
                if let website = library.website, let url = URL(string: website) {
                    Link(website, destination: url)
                        .font(.footnote)
                }
                ForEach(library.licenses, id: \.self) { id in
                    if let licence = licences.licenses[id] {
                        Text(licence.name)
                            .font(.headline)
                        Text(licence.content ?? licence.url ?? "")
                            .font(.footnote.monospaced())
                            .textSelection(.enabled)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
        .navigationTitle(library.name)
        .navigationBarTitleDisplayMode(.inline)
    }
}
