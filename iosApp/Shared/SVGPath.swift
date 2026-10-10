import SwiftUI

/// SVG path data, as the provider logos in HeadroomKit carry it, turned into a SwiftUI `Path`.
/// It reads the commands those logos use: M, L, H, V, C, S, Q, T, A and Z, absolute and relative.
enum SVGPath {
    static func parse(_ data: String) -> Path {
        var parser = Parser(tokens: tokenize(data))
        return parser.parse()
    }

    private enum Token: Equatable {
        case command(Character)
        case number(Double)
    }

    private static func tokenize(_ data: String) -> [Token] {
        var tokens: [Token] = []
        var number = ""
        func flush() {
            if let value = Double(number) { tokens.append(.number(value)) }
            number = ""
        }
        for char in data {
            switch char {
            case "0"..."9", ".":
                // A second dot starts a new number: "1.5.5" is 1.5 and .5.
                if char == ".", number.contains(".") { flush() }
                number.append(char)
            case "-", "+":
                // A sign starts a new number, unless it belongs to an exponent.
                if let last = number.last, last == "e" || last == "E" {
                    number.append(char)
                } else {
                    flush()
                    number.append(char)
                }
            case "e", "E":
                number.append(char)
            case _ where char.isLetter:
                flush()
                tokens.append(.command(char))
            default:
                flush()
            }
        }
        flush()
        return tokens
    }

    private struct Parser {
        let tokens: [Token]
        var index = 0
        var path = Path()
        var current = CGPoint.zero
        var start = CGPoint.zero
        var lastControl: CGPoint?
        var lastCommand: Character = " "

        init(tokens: [Token]) {
            self.tokens = tokens
        }

        mutating func parse() -> Path {
            var command: Character = "M"
            while index < tokens.count {
                if case let .command(next) = tokens[index] {
                    command = next
                    index += 1
                    if command == "Z" || command == "z" {
                        path.closeSubpath()
                        current = start
                        lastControl = nil
                        lastCommand = command
                        continue
                    }
                }
                guard hasNumber else {
                    index += 1
                    continue
                }
                apply(command)
                lastCommand = command
                // After a move, further pairs are lines.
                if command == "M" { command = "L" }
                if command == "m" { command = "l" }
            }
            return path
        }

        private var hasNumber: Bool {
            if index < tokens.count, case .number = tokens[index] { return true }
            return false
        }

        private mutating func number() -> Double {
            guard index < tokens.count, case let .number(value) = tokens[index] else { return 0 }
            index += 1
            return value
        }

        private mutating func point(relative: Bool) -> CGPoint {
            let x = number()
            let y = number()
            return relative ? CGPoint(x: current.x + x, y: current.y + y) : CGPoint(x: x, y: y)
        }

        private mutating func apply(_ command: Character) {
            let relative = command.isLowercase
            switch command.uppercased() {
            case "M":
                current = point(relative: relative)
                start = current
                path.move(to: current)
                lastControl = nil
            case "L":
                current = point(relative: relative)
                path.addLine(to: current)
                lastControl = nil
            case "H":
                let x = number()
                current = CGPoint(x: relative ? current.x + x : x, y: current.y)
                path.addLine(to: current)
                lastControl = nil
            case "V":
                let y = number()
                current = CGPoint(x: current.x, y: relative ? current.y + y : y)
                path.addLine(to: current)
                lastControl = nil
            case "C":
                let control1 = point(relative: relative)
                let control2 = point(relative: relative)
                let end = point(relative: relative)
                path.addCurve(to: end, control1: control1, control2: control2)
                lastControl = control2
                current = end
            case "S":
                let control1 = reflectedControl(after: "CcSs")
                let control2 = point(relative: relative)
                let end = point(relative: relative)
                path.addCurve(to: end, control1: control1, control2: control2)
                lastControl = control2
                current = end
            case "Q":
                let control = point(relative: relative)
                let end = point(relative: relative)
                path.addQuadCurve(to: end, control: control)
                lastControl = control
                current = end
            case "T":
                let control = reflectedControl(after: "QqTt")
                let end = point(relative: relative)
                path.addQuadCurve(to: end, control: control)
                lastControl = control
                current = end
            case "A":
                let radiusX = number()
                let radiusY = number()
                let rotation = number()
                let largeArc = number() != 0
                let sweep = number() != 0
                let end = point(relative: relative)
                addArc(to: end, radiusX: radiusX, radiusY: radiusY, rotation: rotation,
                       largeArc: largeArc, sweep: sweep)
                current = end
                lastControl = nil
            default:
                // An unknown command: skip its numbers rather than draw something wrong.
                while hasNumber { index += 1 }
            }
        }

        /// The first control point of a smooth curve: the last one mirrored, or the current point.
        private func reflectedControl(after previous: String) -> CGPoint {
            guard let control = lastControl, previous.contains(lastCommand) else { return current }
            return CGPoint(x: 2 * current.x - control.x, y: 2 * current.y - control.y)
        }

        /// An SVG elliptical arc, as cubic Béziers (SVG 1.1 implementation notes, F.6).
        private mutating func addArc(
            to end: CGPoint, radiusX: Double, radiusY: Double, rotation: Double,
            largeArc: Bool, sweep: Bool
        ) {
            var rx = abs(radiusX)
            var ry = abs(radiusY)
            guard rx > 0, ry > 0, current != end else {
                path.addLine(to: end)
                return
            }
            let phi = rotation * .pi / 180
            let cosPhi = cos(phi)
            let sinPhi = sin(phi)
            let dx = (current.x - end.x) / 2
            let dy = (current.y - end.y) / 2
            let x1 = cosPhi * dx + sinPhi * dy
            let y1 = -sinPhi * dx + cosPhi * dy
            // Radii too small to reach the end point grow until they do.
            let lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry)
            if lambda > 1 {
                rx *= lambda.squareRoot()
                ry *= lambda.squareRoot()
            }
            let numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
            let denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1
            var factor = (max(0, numerator) / denominator).squareRoot()
            if largeArc == sweep { factor = -factor }
            let cx1 = factor * rx * y1 / ry
            let cy1 = -factor * ry * x1 / rx
            let center = CGPoint(
                x: cosPhi * cx1 - sinPhi * cy1 + (current.x + end.x) / 2,
                y: sinPhi * cx1 + cosPhi * cy1 + (current.y + end.y) / 2
            )
            let startAngle = angle(1, 0, (x1 - cx1) / rx, (y1 - cy1) / ry)
            var delta = angle((x1 - cx1) / rx, (y1 - cy1) / ry, (-x1 - cx1) / rx, (-y1 - cy1) / ry)
            if !sweep, delta > 0 { delta -= 2 * .pi }
            if sweep, delta < 0 { delta += 2 * .pi }

            let segments = Int((abs(delta) / (.pi / 2)).rounded(.up))
            let step = delta / Double(segments)
            let handle = 4.0 / 3.0 * tan(step / 4)
            var theta = startAngle
            for _ in 0..<segments {
                let next = theta + step
                let p1 = ellipsePoint(center, rx, ry, cosPhi, sinPhi, theta)
                let p2 = ellipsePoint(center, rx, ry, cosPhi, sinPhi, next)
                let d1 = ellipseDerivative(rx, ry, cosPhi, sinPhi, theta)
                let d2 = ellipseDerivative(rx, ry, cosPhi, sinPhi, next)
                path.addCurve(
                    to: p2,
                    control1: CGPoint(x: p1.x + handle * d1.x, y: p1.y + handle * d1.y),
                    control2: CGPoint(x: p2.x - handle * d2.x, y: p2.y - handle * d2.y)
                )
                theta = next
            }
        }

        private func angle(_ ux: Double, _ uy: Double, _ vx: Double, _ vy: Double) -> Double {
            atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        }

        private func ellipsePoint(
            _ center: CGPoint, _ rx: Double, _ ry: Double, _ cosPhi: Double, _ sinPhi: Double,
            _ theta: Double
        ) -> CGPoint {
            CGPoint(
                x: center.x + rx * cos(theta) * cosPhi - ry * sin(theta) * sinPhi,
                y: center.y + rx * cos(theta) * sinPhi + ry * sin(theta) * cosPhi
            )
        }

        private func ellipseDerivative(
            _ rx: Double, _ ry: Double, _ cosPhi: Double, _ sinPhi: Double, _ theta: Double
        ) -> CGPoint {
            CGPoint(
                x: -rx * sin(theta) * cosPhi - ry * cos(theta) * sinPhi,
                y: -rx * sin(theta) * sinPhi + ry * cos(theta) * cosPhi
            )
        }
    }
}
