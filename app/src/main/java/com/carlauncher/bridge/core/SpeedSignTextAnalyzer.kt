package com.carlauncher.bridge.core

import android.graphics.Bitmap
import android.graphics.Rect
import com.carlauncher.bridge.model.SpeedSignDetectionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs
import kotlin.math.max

class SpeedSignTextAnalyzer {
    private enum class LayoutType {
        SINGLE,
        TWO_VERTICAL,
        TWO_HORIZONTAL,
        UNKNOWN
    }

    private data class SpeedCandidate(
        val value: Int,
        val box: Rect?,
        val lineText: String,
        val area: Int
    ) {
        val centerX: Int
            get() = box?.centerX() ?: -1

        val centerY: Int
            get() = box?.centerY() ?: -1

        val width: Int
            get() = box?.width() ?: 0

        val height: Int
            get() = box?.height() ?: 0
    }

    private data class DistanceCandidate(
        val meters: Int,
        val box: Rect?,
        val lineText: String
    ) {
        val centerX: Int
            get() = box?.centerX() ?: -1

        val centerY: Int
            get() = box?.centerY() ?: -1
    }

    private data class RawTextFallback(
        val current: Int?,
        val upcoming: Int?,
        val distanceMeters: Int?
    )

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val speedTokenRegex = Regex("(?<!\\d)[0-9oislbz]{2,3}(?!\\d)")
    private val distanceRegex = Regex("(\\d+(?:[\\.,]\\d+)?)\\s*(km|m)(?!\\s*/?h)", RegexOption.IGNORE_CASE)
    private val validSpeeds = setOf(30, 40, 50, 60, 70, 80, 90, 100, 110, 120)

    suspend fun analyze(bitmap: Bitmap, sourceLabel: String = "roi"): SpeedSignDetectionResult? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()

        val speedCandidates = mutableListOf<SpeedCandidate>()
        val distanceCandidates = mutableListOf<DistanceCandidate>()

        var labeledCurrent: SpeedCandidate? = null
        var labeledUpcoming: SpeedCandidate? = null

        result.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                val normalized = normalize(line.text)
                val lineSpeeds = extractSpeedCandidates(
                    line = line,
                    normalizedLine = normalized,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height
                )
                speedCandidates += lineSpeeds

                if (labeledCurrent == null && containsCurrentKeyword(normalized)) {
                    labeledCurrent = lineSpeeds.firstOrNull()
                }
                if (labeledUpcoming == null && containsUpcomingKeyword(normalized)) {
                    labeledUpcoming = lineSpeeds.firstOrNull()
                }

                distanceCandidates += extractDistanceCandidates(line)
            }
        }

        if (speedCandidates.isEmpty() && distanceCandidates.isEmpty()) {
            return null
        }

        val orderedCandidates = dedupeCandidates(speedCandidates)
            .sortedWith(compareBy({ it.box?.centerY() ?: Int.MAX_VALUE }, { it.box?.centerX() ?: Int.MAX_VALUE }))

        val primaryCandidates = selectPrimaryCandidates(orderedCandidates)
        val inferredLayout = inferLayout(primaryCandidates)
        val normalizedLayout = if (inferredLayout == LayoutType.TWO_HORIZONTAL) {
            LayoutType.TWO_VERTICAL
        } else {
            inferredLayout
        }

        val (inferredCurrent, inferredUpcoming) = inferCurrentUpcoming(primaryCandidates, normalizedLayout)

        var current = labeledCurrent ?: inferredCurrent ?: chooseCurrentFallback(orderedCandidates)
        val upcomingRaw = labeledUpcoming ?: inferredUpcoming ?: chooseUpcomingFallback(orderedCandidates, current)
        var upcoming = if (normalizedLayout == LayoutType.SINGLE) null else upcomingRaw
        var distance = chooseDistanceCandidate(
            distances = distanceCandidates,
            current = current,
            upcoming = upcoming,
            layout = normalizedLayout
        )

        val rawFallback = parseRawTextFallback(result.text)
        if (current == null && rawFallback?.current != null) {
            current = SpeedCandidate(
                value = rawFallback.current,
                box = null,
                lineText = "raw_text_fallback",
                area = 0
            )
        }
        if (upcoming == null && rawFallback?.upcoming != null) {
            upcoming = SpeedCandidate(
                value = rawFallback.upcoming,
                box = null,
                lineText = "raw_text_fallback",
                area = 0
            )
        }
        if (distance == null && rawFallback?.distanceMeters != null) {
            distance = DistanceCandidate(
                meters = rawFallback.distanceMeters,
                box = null,
                lineText = "raw_text_fallback"
            )
        }

        if (upcoming != null && distance == null) {
            // Avoid publishing a second speed sign when no distance context is detected.
            upcoming = null
        }

        val layoutType = when {
            upcoming == null -> LayoutType.SINGLE
            normalizedLayout == LayoutType.SINGLE || normalizedLayout == LayoutType.UNKNOWN -> {
                val fallbackLayout = inferLayout(listOfNotNull(current, upcoming))
                if (fallbackLayout == LayoutType.UNKNOWN) {
                    LayoutType.TWO_VERTICAL
                } else if (fallbackLayout == LayoutType.TWO_HORIZONTAL) {
                    LayoutType.TWO_VERTICAL
                } else {
                    fallbackLayout
                }
            }
            else -> normalizedLayout
        }

        val candidateValues = (
            orderedCandidates.map { it.value } + listOfNotNull(current?.value, upcoming?.value)
        ).distinct()

        val debugSummary = buildDebugSummary(
            layout = layoutType,
            current = current,
            upcoming = upcoming,
            distance = distance,
            speedCandidates = orderedCandidates,
            distanceCandidates = distanceCandidates
        )

        return SpeedSignDetectionResult(
            currentLimit = current?.value,
            upcomingLimit = upcoming?.value,
            upcomingDistanceMeters = distance?.meters,
            candidates = candidateValues,
            layoutType = layoutType.asWireName(),
            captureSource = sourceLabel,
            debugSummary = debugSummary,
            rawText = result.text
        )
    }

    fun close() {
        runCatching { recognizer.close() }
    }

    private fun dedupeCandidates(candidates: List<SpeedCandidate>): List<SpeedCandidate> {
        if (candidates.isEmpty()) return emptyList()

        val deduped = mutableListOf<SpeedCandidate>()
        for (candidate in candidates.sortedByDescending { it.area }) {
            val duplicate = deduped.any { existing ->
                existing.value == candidate.value && areBoxesClose(existing.box, candidate.box)
            }
            if (!duplicate) {
                deduped += candidate
            }
        }
        return deduped
    }

    private fun selectPrimaryCandidates(candidates: List<SpeedCandidate>): List<SpeedCandidate> {
        if (candidates.size <= 2) return candidates

        val ranked = candidates.sortedByDescending { it.area }.take(6)
        val pair = chooseBestPair(ranked) ?: return listOf(ranked.first())
        return listOf(pair.first, pair.second)
    }

    private fun chooseBestPair(candidates: List<SpeedCandidate>): Pair<SpeedCandidate, SpeedCandidate>? {
        if (candidates.size < 2) return null

        var bestPair: Pair<SpeedCandidate, SpeedCandidate>? = null
        var bestScore = Int.MAX_VALUE

        for (i in 0 until candidates.lastIndex) {
            for (j in i + 1 until candidates.size) {
                val score = scorePair(candidates[i], candidates[j])
                if (score < bestScore) {
                    bestScore = score
                    bestPair = candidates[i] to candidates[j]
                }
            }
        }

        return bestPair
    }

    private fun scorePair(a: SpeedCandidate, b: SpeedCandidate): Int {
        val boxA = a.box ?: return Int.MAX_VALUE / 2
        val boxB = b.box ?: return Int.MAX_VALUE / 2

        val dx = abs(boxA.centerX() - boxB.centerX())
        val dy = abs(boxA.centerY() - boxB.centerY())
        val dominantAxisPenalty = minOf(dx, dy) * 2
        val separationPenalty = (dx + dy) / 3

        val largerArea = max(a.area, b.area).coerceAtLeast(1)
        val areaPenalty = (abs(a.area - b.area) * 100) / largerArea

        val nearDuplicatePenalty = if (
            dx < max(boxA.width(), boxB.width()) / 3 &&
            dy < max(boxA.height(), boxB.height()) / 3
        ) {
            4000
        } else {
            0
        }

        return dominantAxisPenalty + separationPenalty + areaPenalty + nearDuplicatePenalty
    }

    private fun inferLayout(candidates: List<SpeedCandidate>): LayoutType {
        if (candidates.isEmpty()) return LayoutType.UNKNOWN
        if (candidates.size == 1) return LayoutType.SINGLE

        val first = candidates[0]
        val second = candidates[1]
        val firstBox = first.box ?: return LayoutType.UNKNOWN
        val secondBox = second.box ?: return LayoutType.UNKNOWN

        val dx = abs(firstBox.centerX() - secondBox.centerX())
        val dy = abs(firstBox.centerY() - secondBox.centerY())

        return when {
            dy >= (dx * 1.2).toInt() -> LayoutType.TWO_VERTICAL
            dx >= (dy * 1.2).toInt() -> LayoutType.TWO_HORIZONTAL
            dy >= dx -> LayoutType.TWO_VERTICAL
            else -> LayoutType.TWO_HORIZONTAL
        }
    }

    private fun inferCurrentUpcoming(
        candidates: List<SpeedCandidate>,
        layout: LayoutType
    ): Pair<SpeedCandidate?, SpeedCandidate?> {
        if (candidates.isEmpty()) return null to null
        if (candidates.size == 1) return candidates.first() to null

        val pair = candidates.take(2)
        return when (layout) {
            LayoutType.TWO_VERTICAL -> {
                val sorted = pair.sortedBy { it.centerY }
                sorted.getOrNull(0) to sorted.getOrNull(1)
            }

            LayoutType.TWO_HORIZONTAL -> {
                val sorted = pair.sortedBy { it.centerX }
                sorted.getOrNull(0) to sorted.getOrNull(1)
            }

            LayoutType.SINGLE -> pair.firstOrNull() to null
            LayoutType.UNKNOWN -> {
                val current = chooseCurrentFallback(pair)
                current to chooseUpcomingFallback(pair, current)
            }
        }
    }

    private fun extractSpeedCandidates(
        line: Text.Line,
        normalizedLine: String,
        imageWidth: Int,
        imageHeight: Int
    ): List<SpeedCandidate> {
        val box = line.boundingBox ?: return emptyList()
        if (!isCandidateBoxAcceptable(box, imageWidth, imageHeight)) return emptyList()

        return speedTokenRegex.findAll(normalizedLine)
            .mapNotNull { match ->
                val value = parseSpeedValue(match.value) ?: return@mapNotNull null
                SpeedCandidate(
                    value = value,
                    box = box,
                    lineText = line.text,
                    area = box.width() * box.height()
                )
            }
            .toList()
    }

    private fun parseSpeedValue(token: String): Int? {
        val normalizedToken = token
            .lowercase()
            .replace('o', '0')
            .replace('i', '1')
            .replace('l', '1')
            .replace('s', '5')
            .replace('b', '8')
            .replace('z', '2')

        val rawValue = normalizedToken.toIntOrNull() ?: return null
        return mapToValidSpeed(rawValue)
    }

    private fun mapToValidSpeed(rawValue: Int): Int? {
        if (rawValue in validSpeeds) return rawValue
        if (rawValue !in 25..125) return null

        val nearest = validSpeeds.minByOrNull { abs(it - rawValue) } ?: return null
        return if (abs(nearest - rawValue) <= 4) nearest else null
    }

    private fun isCandidateBoxAcceptable(box: Rect, imageWidth: Int, imageHeight: Int): Boolean {
        val width = box.width()
        val height = box.height()
        val area = width * height

        val minWidth = (imageWidth * 0.015f).toInt().coerceAtLeast(8)
        val minHeight = (imageHeight * 0.02f).toInt().coerceAtLeast(10)
        val maxWidth = (imageWidth * 0.8f).toInt().coerceAtLeast(minWidth)
        val maxHeight = (imageHeight * 0.9f).toInt().coerceAtLeast(minHeight)
        val minArea = (imageWidth * imageHeight * 0.0006f).toInt().coerceAtLeast(70)

        return width in minWidth..maxWidth &&
            height in minHeight..maxHeight &&
            area >= minArea
    }

    private fun chooseCurrentFallback(candidates: List<SpeedCandidate>): SpeedCandidate? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        val byArea = candidates.sortedByDescending { it.area }
        val largest = byArea[0]
        val second = byArea[1]
        if (largest.area > 0 && largest.area >= (second.area * 1.15).toInt()) {
            return largest
        }

        return candidates.first()
    }

    private fun chooseUpcomingFallback(
        candidates: List<SpeedCandidate>,
        current: SpeedCandidate?
    ): SpeedCandidate? {
        if (current == null) return candidates.firstOrNull()

        val others = candidates.filterNot { isSameCandidate(it, current) }
        if (others.isEmpty()) return null

        return others.minByOrNull { candidate ->
            scoreUpcomingFallbackCandidate(candidate, current)
        }
    }

    private fun scoreUpcomingFallbackCandidate(candidate: SpeedCandidate, current: SpeedCandidate): Int {
        val dx = abs(candidate.centerX - current.centerX)
        val dy = abs(candidate.centerY - current.centerY)
        val distanceScore = dx + dy
        val areaPenalty = if (candidate.area > (current.area * 1.15).toInt()) 300 else 0
        return distanceScore + areaPenalty
    }

    private fun extractDistanceCandidates(line: Text.Line): List<DistanceCandidate> {
        return distanceRegex.findAll(normalize(line.text))
            .mapNotNull { match ->
                val number = match.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
                    ?: return@mapNotNull null
                val unit = match.groupValues.getOrNull(2)?.lowercase().orEmpty()
                val meters = when (unit) {
                    "km" -> (number * 1000.0).toInt()
                    "m" -> number.toInt()
                    else -> return@mapNotNull null
                }
                DistanceCandidate(meters = meters, box = line.boundingBox, lineText = line.text)
            }
            .toList()
    }

    private fun parseRawTextFallback(rawText: String): RawTextFallback? {
        val normalized = normalize(rawText)
        if (normalized.isBlank()) return null

        val speeds = speedTokenRegex.findAll(normalized)
            .mapNotNull { match ->
                val value = parseSpeedValue(match.value) ?: return@mapNotNull null
                if (value in validSpeeds) value else null
            }
            .distinct()
            .toList()

        val distanceMeters = distanceRegex.find(normalized)?.let { match ->
            val number = match.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull() ?: return@let null
            when (match.groupValues.getOrNull(2)?.lowercase().orEmpty()) {
                "km" -> (number * 1000.0).toInt()
                "m" -> number.toInt()
                else -> null
            }
        }

        if (speeds.isEmpty() && distanceMeters == null) return null

        return RawTextFallback(
            current = speeds.getOrNull(0),
            upcoming = speeds.getOrNull(1),
            distanceMeters = distanceMeters
        )
    }

    private fun chooseDistanceCandidate(
        distances: List<DistanceCandidate>,
        current: SpeedCandidate?,
        upcoming: SpeedCandidate?,
        layout: LayoutType
    ): DistanceCandidate? {
        if (distances.isEmpty() || upcoming == null) return null
        val upcomingBox = upcoming.box ?: return distances.first()
        val currentBox = current?.box

        val (anchorX, anchorY) = when (layout) {
            LayoutType.TWO_HORIZONTAL -> {
                val centerX = if (currentBox != null) {
                    (currentBox.centerX() + upcomingBox.centerX()) / 2
                } else {
                    upcomingBox.centerX()
                }
                val baselineY = max(currentBox?.bottom ?: upcomingBox.bottom, upcomingBox.bottom)
                centerX to baselineY
            }

            LayoutType.TWO_VERTICAL -> {
                upcomingBox.centerX() to upcomingBox.bottom
            }

            LayoutType.SINGLE,
            LayoutType.UNKNOWN -> {
                upcomingBox.centerX() to upcomingBox.bottom
            }
        }

        return distances.minByOrNull { candidate ->
            val box = candidate.box ?: return@minByOrNull Int.MAX_VALUE
            val dx = abs(box.centerX() - anchorX)
            val dy = abs(box.centerY() - anchorY)
            val belowPenalty = if (box.centerY() >= anchorY - 10) 0 else 700
            belowPenalty + dy + dx
        } ?: distances.first()
    }

    private fun buildDebugSummary(
        layout: LayoutType,
        current: SpeedCandidate?,
        upcoming: SpeedCandidate?,
        distance: DistanceCandidate?,
        speedCandidates: List<SpeedCandidate>,
        distanceCandidates: List<DistanceCandidate>
    ): String {
        val speedDebug = speedCandidates.take(4).joinToString(";") {
            "${it.value}@${it.centerX},${it.centerY}"
        }
        val distanceDebug = distanceCandidates.take(3).joinToString(";") {
            "${it.meters}@${it.centerX},${it.centerY}"
        }

        return "layout=${layout.asWireName()} " +
            "cur=${current?.value ?: "-"} " +
            "next=${upcoming?.value ?: "-"} " +
            "dist=${distance?.meters ?: "-"} " +
            "speeds=[$speedDebug] " +
            "dists=[$distanceDebug]"
    }

    private fun containsCurrentKeyword(text: String): Boolean {
        return text.contains("current") ||
            text.contains("hien tai") ||
            text.contains("gioi han hien tai")
    }

    private fun containsUpcomingKeyword(text: String): Boolean {
        return text.contains("upcoming") ||
            text.contains("next") ||
            text.contains("sap toi") ||
            text.contains("con lai")
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace('đ', 'd')
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isSameCandidate(a: SpeedCandidate, b: SpeedCandidate): Boolean {
        if (a.value != b.value) return false
        val boxA = a.box
        val boxB = b.box
        return if (boxA == null || boxB == null) {
            a.lineText == b.lineText
        } else {
            boxA.centerX() == boxB.centerX() && boxA.centerY() == boxB.centerY()
        }
    }

    private fun areBoxesClose(a: Rect?, b: Rect?): Boolean {
        if (a == null || b == null) return false
        val dx = abs(a.centerX() - b.centerX())
        val dy = abs(a.centerY() - b.centerY())
        return dx <= 18 && dy <= 18
    }

    private fun LayoutType.asWireName(): String {
        return when (this) {
            LayoutType.SINGLE -> "single"
            LayoutType.TWO_VERTICAL -> "vertical"
            LayoutType.TWO_HORIZONTAL -> "horizontal"
            LayoutType.UNKNOWN -> "unknown"
        }
    }
}
