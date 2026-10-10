// Renders the iOS app icon from the Android launcher icon's design
// (app/src/main/res/drawable/ic_launcher_foreground.xml): a quota ring 75% used, its headroom left
// as a faint track, and a dot, in white on the launcher blue. Android shows the middle 72 of the
// icon's 108 units; this renders the same crop.
//
// Run from iosApp: swift scripts/render-app-icon.swift Headroom/Assets.xcassets/AppIcon.appiconset/AppIcon.png
import AppKit

let size = 1024.0
let unit = size / 72 // One Android viewport unit; the crop starts at (18, 18).
func point(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: (x - 18) * unit, y: size - (y - 18) * unit) }

let image = NSImage(size: NSSize(width: size, height: size))
image.lockFocus()
let context = NSGraphicsContext.current!.cgContext
context.setFillColor(CGColor(srgbRed: 0x35 / 255, green: 0x53 / 255, blue: 0xB0 / 255, alpha: 1))
context.fill(CGRect(x: 0, y: 0, width: size, height: size))

let center = point(54, 54)
let radius = 23 * unit
context.setLineWidth(9 * unit)
context.setLineCap(.round)
// The headroom left: the quarter from 9 o'clock to 12 o'clock, faint.
context.setStrokeColor(CGColor(gray: 1, alpha: 0.35))
context.addArc(center: center, radius: radius, startAngle: .pi, endAngle: .pi / 2, clockwise: true)
context.strokePath()
// The share used: the other three quarters, from 12 o'clock clockwise round to 9 o'clock.
// The context's y axis points up, so a decreasing angle is clockwise.
context.setStrokeColor(CGColor(gray: 1, alpha: 1))
context.addArc(center: center, radius: radius, startAngle: .pi / 2, endAngle: .pi, clockwise: true)
context.strokePath()
// The dot in the middle.
context.setFillColor(CGColor(gray: 1, alpha: 1))
context.fillEllipse(in: CGRect(x: center.x - 7 * unit, y: center.y - 7 * unit, width: 14 * unit, height: 14 * unit))
image.unlockFocus()

let bitmap = NSBitmapImageRep(data: image.tiffRepresentation!)!
let png = bitmap.representation(using: .png, properties: [:])!
try! png.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
