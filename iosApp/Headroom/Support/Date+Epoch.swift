import Foundation

extension Date {
    /// A date from the seconds since 1970 that HeadroomKit uses for instants.
    init(epochSeconds: Int64) {
        self.init(timeIntervalSince1970: TimeInterval(epochSeconds))
    }
}
