import SwiftUI

/// A thin mark at `fraction` of the width, where usage would be at even pace.
struct PaceTick: Shape {
    let fraction: Double

    func path(in rect: CGRect) -> Path {
        let x = rect.minX + rect.width * min(max(fraction, 0), 1)
        return Path(CGRect(x: x - 1, y: rect.minY, width: 2, height: rect.height))
    }
}
