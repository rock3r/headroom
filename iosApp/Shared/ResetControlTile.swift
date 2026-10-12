import Foundation

/// Controls are read independently of publishing: derive the relative time at each read.
enum ResetControlTile {
    static func make(name: String, resetsAt: Date?, now: Date = .now) -> WidgetSnapshot.Tile? {
        guard let resetsAt, resetsAt > now else { return nil }
        let totalMinutes = Int(resetsAt.timeIntervalSince(now) / 60)
        let days = totalMinutes / 1440
        let hours = totalMinutes / 60 % 24
        let minutes = totalMinutes % 60
        let countdown = days > 0 ? "\(days)d \(hours)h" : String(format: "%dh %02dm", hours, minutes)
        return WidgetSnapshot.Tile(name: name, value: String(localized: "in \(countdown)"))
    }
}
