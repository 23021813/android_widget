import Foundation
import Vision
import AppKit

struct OCRLine {
    let text: String
    let x: Double
    let y: Double
    let width: Double
    let height: Double

    var centerX: Double { x + width / 2.0 }
    var centerY: Double { y + height / 2.0 }
}

struct SpeedCandidate {
    let value: Int
    let x: Double
    let y: Double
    let area: Double
}

struct DistanceCandidate {
    let meters: Int
    let x: Double
    let y: Double
}

let validSpeedLimits: Set<Int> = [30, 40, 50, 60, 70, 80, 90, 100, 110, 120]
let speedRegex = try! NSRegularExpression(pattern: "\\b(\\d{2,3})\\b")
let distanceRegex = try! NSRegularExpression(pattern: "(\\d+(?:[\\.,]\\d+)?)\\s*(km|m)", options: [.caseInsensitive])

func toTopLeftRect(_ normalized: CGRect, imageWidth: Double, imageHeight: Double) -> CGRect {
    let x = normalized.origin.x * imageWidth
    let width = normalized.size.width * imageWidth
    let height = normalized.size.height * imageHeight
    let yBottom = normalized.origin.y * imageHeight
    let yTop = imageHeight - (yBottom + height)
    return CGRect(x: x, y: yTop, width: width, height: height)
}

func extractSpeedCandidates(from lines: [OCRLine]) -> [SpeedCandidate] {
    var result: [SpeedCandidate] = []
    for line in lines {
        let text = line.text
        let nsText = text as NSString
        let range = NSRange(location: 0, length: nsText.length)
        let matches = speedRegex.matches(in: text, options: [], range: range)
        for match in matches {
            guard match.numberOfRanges >= 2 else { continue }
            let raw = nsText.substring(with: match.range(at: 1))
            guard let value = Int(raw), validSpeedLimits.contains(value) else { continue }
                result.append(
                    SpeedCandidate(
                        value: value,
                        x: line.centerX,
                        y: line.centerY,
                        area: line.width * line.height
                    )
                )
        }
    }

    var seen = Set<String>()
    let deduped = result.filter { candidate in
        let key = "\(candidate.value)_\(Int(candidate.x))_\(Int(candidate.y))"
        if seen.contains(key) { return false }
        seen.insert(key)
        return true
    }

    return deduped.sorted {
        if abs($0.y - $1.y) < 1.0 {
            return $0.x < $1.x
        }
        return $0.y < $1.y
    }
}

func chooseCurrentCandidate(_ candidates: [SpeedCandidate]) -> SpeedCandidate? {
    guard !candidates.isEmpty else { return nil }
    guard candidates.count > 1 else { return candidates.first }

    let byArea = candidates.sorted { $0.area > $1.area }
    let largest = byArea[0]
    let second = byArea[1]
    if largest.area > 0 && largest.area >= second.area * 1.15 {
        return largest
    }

    return candidates.first
}

func chooseUpcomingCandidate(_ candidates: [SpeedCandidate], current: SpeedCandidate?) -> SpeedCandidate? {
    guard let current else { return candidates.first }
    let others = candidates.filter {
        !($0.value == current.value && abs($0.x - current.x) < 1.0 && abs($0.y - current.y) < 1.0)
    }
    guard !others.isEmpty else { return nil }

    return others.min { lhs, rhs in
        scoreUpcomingCandidate(lhs, current: current) < scoreUpcomingCandidate(rhs, current: current)
    }
}

func scoreUpcomingCandidate(_ candidate: SpeedCandidate, current: SpeedCandidate) -> Double {
    let dx = candidate.x - current.x
    let dy = candidate.y - current.y
    let directionPenalty = (dx >= -5.0 || dy >= -5.0) ? 0.0 : 1200.0
    let distance = abs(dx) + abs(dy)
    let areaPenalty = candidate.area > current.area * 1.15 ? 300.0 : 0.0
    return directionPenalty + distance + areaPenalty
}

func extractDistanceCandidates(from lines: [OCRLine]) -> [DistanceCandidate] {
    var result: [DistanceCandidate] = []

    for line in lines {
        let text = line.text.lowercased()
        let nsText = text as NSString
        let range = NSRange(location: 0, length: nsText.length)
        let matches = distanceRegex.matches(in: text, options: [], range: range)

        for match in matches {
            guard match.numberOfRanges >= 3 else { continue }
            let numberRaw = nsText.substring(with: match.range(at: 1)).replacingOccurrences(of: ",", with: ".")
            let unitRaw = nsText.substring(with: match.range(at: 2))
            guard let value = Double(numberRaw) else { continue }

            let meters: Int
            if unitRaw == "km" {
                meters = Int(value * 1000.0)
            } else {
                meters = Int(value)
            }

            result.append(DistanceCandidate(meters: meters, x: line.centerX, y: line.centerY))
        }
    }

    return result
}

func pickDistance(_ candidates: [DistanceCandidate], upcoming: SpeedCandidate?) -> Int? {
    guard !candidates.isEmpty else { return nil }
    guard let upcoming = upcoming else { return candidates.first?.meters }

    return candidates.min { a, b in
        let da = abs(a.x - upcoming.x) + abs(a.y - upcoming.y)
        let db = abs(b.x - upcoming.x) + abs(b.y - upcoming.y)
        return da < db
    }?.meters
}

func quadrantName(for x: Double, y: Double, width: Double, height: Double) -> String {
    let left = x < width / 2.0
    let top = y < height / 2.0
    if top && left { return "top-left" }
    if top && !left { return "top-right" }
    if !top && left { return "bottom-left" }
    return "bottom-right"
}

func recognizeLines(imagePath: String) throws -> (Double, Double, [OCRLine]) {
    let url = URL(fileURLWithPath: imagePath)
    guard let image = NSImage(contentsOf: url),
          let cgImage = image.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
        throw NSError(domain: "OCR", code: 1, userInfo: [NSLocalizedDescriptionKey: "Cannot load image: \(imagePath)"])
    }

    let width = Double(cgImage.width)
    let height = Double(cgImage.height)

    let request = VNRecognizeTextRequest()
    request.recognitionLevel = .accurate
    request.usesLanguageCorrection = false
    request.recognitionLanguages = ["vi-VN", "en-US"]

    let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
    try handler.perform([request])

    let observations = request.results ?? []
    var lines: [OCRLine] = []

    for obs in observations {
        guard let candidate = obs.topCandidates(1).first else { continue }
        let rect = toTopLeftRect(obs.boundingBox, imageWidth: width, imageHeight: height)
        lines.append(
            OCRLine(
                text: candidate.string,
                x: rect.origin.x,
                y: rect.origin.y,
                width: rect.size.width,
                height: rect.size.height
            )
        )
    }

    return (width, height, lines)
}

func run(imagePath: String) throws {
    let (width, height, lines) = try recognizeLines(imagePath: imagePath)

    var quadrants: [String: [OCRLine]] = [
        "top-left": [],
        "top-right": [],
        "bottom-left": [],
        "bottom-right": []
    ]

    for line in lines {
        let name = quadrantName(for: line.centerX, y: line.centerY, width: width, height: height)
        quadrants[name, default: []].append(line)
    }

    let order = ["top-left", "top-right", "bottom-left", "bottom-right"]
    print("Image: \(imagePath)")
    print("Size: \(Int(width))x\(Int(height))")
    print("---")

    for name in order {
        let qLines = quadrants[name] ?? []
        let speeds = extractSpeedCandidates(from: qLines)
        let distances = extractDistanceCandidates(from: qLines)

        let currentCandidate = chooseCurrentCandidate(speeds)
        let upcomingCandidate = chooseUpcomingCandidate(speeds, current: currentCandidate)

        let current = currentCandidate?.value
        let upcoming = upcomingCandidate?.value
        let distance = pickDistance(distances, upcoming: upcomingCandidate)

        print("[\(name)]")
        print("  OCR lines: \(qLines.map { $0.text })")
        print("  speed candidates: \(speeds.map { $0.value })")
        print("  distance candidates(m): \(distances.map { $0.meters })")
        print("  extracted => current=\(current.map(String.init) ?? "nil"), upcoming=\(upcoming.map(String.init) ?? "nil"), distance=\(distance.map { "\($0)m" } ?? "nil")")
        print("")
    }
}

if CommandLine.arguments.count < 2 {
    print("Usage: swift tools/speed_sign_widget_test.swift <image_path>")
    exit(2)
}

let imagePath = CommandLine.arguments[1]
do {
    try run(imagePath: imagePath)
} catch {
    fputs("Error: \(error)\n", stderr)
    exit(1)
}
