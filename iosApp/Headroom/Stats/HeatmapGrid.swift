import SwiftUI

/// Seven rows of 24 hours, Monday first, each cell as dark as the quota burned in it.
struct HeatmapGrid: View {
    let cells: [Double]

    var body: some View {
        Canvas { context, size in
            let peak = max(cells.max() ?? 0, 0.0001)
            let width = size.width / 24
            let height = size.height / 7
            for (index, value) in cells.enumerated() {
                let rect = CGRect(x: CGFloat(index % 24) * width, y: CGFloat(index / 24) * height,
                                  width: width - 1.5, height: height - 1.5)
                let shading = GraphicsContext.Shading.style(.tint)
                context.opacity = 0.08 + 0.92 * (value / peak)
                context.fill(Path(roundedRect: rect, cornerRadius: 2), with: shading)
            }
        }
    }
}
