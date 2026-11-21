import SwiftUI
import UIKit

/// A sideways drag that only starts when it moves more across than up or down, so a list around
/// the chart still scrolls. Reports the drag's horizontal travel, then glides on after the finger
/// lifts, slowing down as a scroll view does, and reports nil once it rests. A touch stops the
/// glide. `onChange` returns false when the travel hit an edge, which ends the glide there.
struct SidewaysPan: UIGestureRecognizerRepresentable {
    let onChange: (CGFloat?) -> Bool

    func makeUIGestureRecognizer(context: Context) -> UIPanGestureRecognizer {
        let pan = UIPanGestureRecognizer()
        pan.delegate = context.coordinator
        context.coordinator.onChange = onChange
        return pan
    }

    func updateUIGestureRecognizer(_ recognizer: UIPanGestureRecognizer, context: Context) {
        context.coordinator.onChange = onChange
    }

    func makeCoordinator(converter: CoordinateSpaceConverter) -> Coordinator {
        Coordinator()
    }

    func handleUIGestureRecognizerAction(_ recognizer: UIPanGestureRecognizer, context: Context) {
        let coordinator = context.coordinator
        switch recognizer.state {
        case .began, .changed:
            coordinator.stopGlide(report: false)
            _ = coordinator.onChange(recognizer.translation(in: recognizer.view).x)
        case .ended:
            coordinator.glide(in: recognizer.view, from: recognizer.translation(in: recognizer.view).x,
                              velocity: recognizer.velocity(in: recognizer.view).x)
        case .cancelled, .failed:
            _ = coordinator.onChange(nil)
        default: break
        }
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var onChange: (CGFloat?) -> Bool = { _ in false }
        private var link: CADisplayLink?
        private weak var view: UIView?
        private var travel: CGFloat = 0
        private var velocity: CGFloat = 0

        /// How much speed is left after each millisecond: UIScrollView's normal deceleration.
        private static let decelerationRate = 0.998
        /// Points per second below which the glide stops.
        private static let restingSpeed: CGFloat = 15

        func gestureRecognizerShouldBegin(_ recognizer: UIGestureRecognizer) -> Bool {
            guard let pan = recognizer as? UIPanGestureRecognizer else { return true }
            let velocity = pan.velocity(in: pan.view)
            return abs(velocity.x) > abs(velocity.y)
        }

        func gestureRecognizer(_ recognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            stopGlide(report: true)
            return true
        }

        func glide(in view: UIView?, from travel: CGFloat, velocity: CGFloat) {
            self.view = view
            self.travel = travel
            self.velocity = velocity
            guard abs(velocity) > Self.restingSpeed, onChange(travel) else {
                _ = onChange(nil)
                return
            }
            let link = CADisplayLink(target: self, selector: #selector(step))
            link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
            link.add(to: .main, forMode: .common)
            self.link = link
        }

        func stopGlide(report: Bool) {
            guard let link else { return }
            link.invalidate()
            self.link = nil
            if report { _ = onChange(nil) }
        }

        @objc private func step(_ link: CADisplayLink) {
            let elapsed = link.targetTimestamp - link.timestamp
            let decay = pow(Self.decelerationRate, elapsed * 1000)
            // The distance covered while the speed decays over this frame.
            travel += velocity * (decay - 1) / log(Self.decelerationRate) / 1000
            velocity *= decay
            // The chart left the screen: stop rather than keep moving it unseen.
            if view?.window == nil || !onChange(travel) || abs(velocity) < Self.restingSpeed {
                stopGlide(report: true)
            }
        }
    }
}

/// A long press that then follows the finger. Reports where the finger is, in the space of the
/// view the gesture is on, then nil when it lifts.
struct PressAndDrag: UIGestureRecognizerRepresentable {
    let onChange: (CGPoint?) -> Void

    func makeUIGestureRecognizer(context: Context) -> UILongPressGestureRecognizer {
        let press = UILongPressGestureRecognizer()
        press.minimumPressDuration = 0.3
        return press
    }

    func handleUIGestureRecognizerAction(_ recognizer: UILongPressGestureRecognizer, context: Context) {
        switch recognizer.state {
        case .began, .changed: onChange(context.converter.localLocation)
        case .ended, .cancelled, .failed: onChange(nil)
        default: break
        }
    }
}
