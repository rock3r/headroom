import HeadroomKit
import SwiftUI

/// "5 pts over pace", "On pace" or "Just reset", as the Android pace chip.
struct PaceChipView: View {
    let window: WindowUi

    var body: some View {
        if let chip = window.paceChip {
            Text(text(chip))
                .font(.caption.bold())
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .foregroundStyle(foreground(chip))
                .background(foreground(chip).opacity(0.14), in: .capsule)
        }
    }

    private func text(_ chip: String) -> LocalizedStringKey {
        let points = Int(window.pacePoints)
        return switch chip {
        case "over": points == 1 ? "1 pt over pace" : "\(points) pts over pace"
        case "under": points == 1 ? "1 pt under pace" : "\(points) pts under pace"
        case "justReset": "Just reset"
        default: "On pace"
        }
    }

    private func foreground(_ chip: String) -> Color {
        switch chip {
        case "over": .red
        case "under": .green
        case "justReset": .accentColor
        default: .secondary
        }
    }
}
