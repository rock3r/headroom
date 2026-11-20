import SwiftUI

/// Seven rows of 24 hours, Monday first, each cell as bright as the quota burned in it.
struct HeatmapGrid: View {
    static let hours = 24
    static let days = 7

    let cells: [Double]
    @Binding var selected: Int?

    var body: some View {
        GeometryReader { proxy in
            Canvas { context, size in
                let peak = max(cells.max() ?? 0, 0.0001)
                let width = size.width / Double(Self.hours)
                let height = size.height / Double(Self.days)
                guard width > 1.5, height > 1.5 else { return }
                for (index, value) in cells.enumerated() {
                    let rect = CGRect(x: CGFloat(index % Self.hours) * width, y: CGFloat(index / Self.hours) * height,
                                      width: width - 1.5, height: height - 1.5)
                    let shape = Path(roundedRect: rect, cornerRadius: 2)
                    context.opacity = 0.08 + 0.92 * (value / peak)
                    context.fill(shape, with: .style(.tint))
                    if index == selected {
                        context.opacity = 1
                        context.stroke(Path(roundedRect: rect.insetBy(dx: 0.75, dy: 0.75), cornerRadius: 1.25),
                                       with: .color(.primary), lineWidth: 1.5)
                    }
                }
            }
            .contentShape(.rect)
            .onTapGesture { location in
                select(at: location, in: proxy.size)
            }
        }
    }

    private func select(at location: CGPoint, in size: CGSize) {
        guard size.width > 0, size.height > 0,
              CGRect(origin: .zero, size: size).contains(location) else { return }
        let column = Int(location.x / size.width * Double(Self.hours))
        let row = Int(location.y / size.height * Double(Self.days))
        let index = row * Self.hours + column
        if cells.indices.contains(index) {
            selected = index
        }
    }
}
