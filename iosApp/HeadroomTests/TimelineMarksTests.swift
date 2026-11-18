import Foundation
import HeadroomKit
import SwiftUI
import Testing
import UIKit
@testable import HeadroomApp

struct TimelineMarksTests {
    @Test func `perRow fits as many marks and gaps as the width allows`() {
        // 329pt width with 10pt marks and 4pt gaps: floor((329 + 4) / 14) = 23.
        #expect(TimelineMarks.perRow(width: 329, mark: 10, gap: 4) == 23)
        // Exact fit for 3 marks: 10 + 4 + 10 + 4 + 10 = 38.
        #expect(TimelineMarks.perRow(width: 38, mark: 10, gap: 4) == 3)
        // Too narrow for 1 full mark still yields 1 so a row never divides by zero.
        #expect(TimelineMarks.perRow(width: 4, mark: 10, gap: 4) == 1)
        #expect(TimelineMarks.perRow(width: 0, mark: 10, gap: 4) == 1)
    }

    @Test func `maxMarks multiplies perRow by maxRows`() {
        #expect(TimelineMarks.maxMarks(width: 329, mark: 10, gap: 4, maxRows: 2) == 46)
    }

    @Test func `visible keeps the latest marks in oldest-first order`() {
        let timeline = [true, false, false, true, false]
        #expect(TimelineMarks.visible(timeline, capacity: 3) == [false, true, false])
        #expect(TimelineMarks.visible(timeline, capacity: 10) == timeline)
        #expect(TimelineMarks.visible(timeline, capacity: 0) == [])
    }

    @Test func `legend inflects the shown reset count when trimmed`() {
        #expect(
            ResetScoreCard.legend(shownCount: 1, totalCount: 5)
                == "Your latest 1 reset, oldest first. Squares hit the limit."
        )
        #expect(
            ResetScoreCard.legend(shownCount: 48, totalCount: 168)
                == "Your latest 48 resets, oldest first. Squares hit the limit."
        )
        #expect(
            ResetScoreCard.legend(shownCount: 5, totalCount: 5)
                == "One mark per reset, oldest first. Squares hit the limit."
        )
    }

    @Test func `height matches one or two rows of marks`() {
        #expect(TimelineMarks.height(count: 0, perRow: 20) == 10)
        #expect(TimelineMarks.height(count: 10, perRow: 20) == 10)
        #expect(TimelineMarks.height(count: 21, perRow: 20) == 24)
    }

    @MainActor @Test func `reset score card shrinks cleanly from wide to narrow without overflowing`() throws {
        let timeline = (0 ..< 120).map { KotlinBoolean(bool: $0 % 5 == 0) }
        let score = ResetScoreUi(total: 120, clean: 96, streak: 4, timeline: timeline)
        let controller = UIHostingController(rootView: List { ResetScoreCard(score: score) })
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 640, height: 844))
        window.rootViewController = controller
        window.makeKeyAndVisible()
        defer {
            window.isHidden = true
            window.rootViewController = nil
        }

        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        let wideSize = controller.sizeThatFits(in: CGSize(width: 640, height: 844))
        #expect(wideSize.width <= 640)

        window.frame = CGRect(x: 0, y: 0, width: 320, height: 844)
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        let narrowSize = controller.sizeThatFits(in: CGSize(width: 320, height: 844))
        #expect(narrowSize.width <= 320)
        let collection = try #require(findCollectionView(in: controller.view))
        for cell in collection.visibleCells {
            #expect(cell.frame.width <= 320)
            for subview in cell.contentView.subviews {
                #expect(subview.frame.maxX <= cell.contentView.bounds.width + 0.5)
            }
        }
    }

    @MainActor private func findCollectionView(in view: UIView) -> UICollectionView? {
        if let collection = view as? UICollectionView { return collection }
        for subview in view.subviews {
            if let found = findCollectionView(in: subview) { return found }
        }
        return nil
    }
}
