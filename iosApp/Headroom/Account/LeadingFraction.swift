import SwiftUI

/// The leading `fraction` of a capsule, for the used share of a bar.
struct LeadingFraction: Shape {
    let fraction: Double

    func path(in rect: CGRect) -> Path {
        let width = rect.width * min(max(fraction, 0), 1)
        return Capsule().path(in: CGRect(x: rect.minX, y: rect.minY, width: width, height: rect.height))
    }
}
