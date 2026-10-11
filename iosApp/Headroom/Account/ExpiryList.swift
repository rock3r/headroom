import HeadroomKit
import SwiftUI

/// When each reset of a pool expires, one short line each, as HeadroomKit lists them.
struct ExpiryList: View {
    let lines: [ExpiryLineUi]

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                Text(text(line))
            }
        }
        .font(.footnote)
        .foregroundStyle(.secondary)
    }

    private func text(_ line: ExpiryLineUi) -> String {
        let count = Int(line.count)
        switch line.kind {
        case "at":
            let when = line.atEpochSeconds.map { Formats.long(Date(epochSeconds: $0.int64Value)) } ?? ""
            return count == 1 ? String(localized: "Expires \(when)") : String(localized: "\(count) expire \(when)")
        case "noExpiry":
            return count == 1 ? String(localized: "No expiry") : String(localized: "\(count) with no expiry")
        default:
            return String(localized: "and \(count) more")
        }
    }
}
