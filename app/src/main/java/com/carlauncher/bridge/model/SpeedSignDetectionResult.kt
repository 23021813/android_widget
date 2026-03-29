package com.carlauncher.bridge.model

data class SpeedSignDetectionResult(
    val currentLimit: Int? = null,
    val upcomingLimit: Int? = null,
    val upcomingDistanceMeters: Int? = null,
    val candidates: List<Int> = emptyList(),
    val layoutType: String = "unknown",
    val captureSource: String = "roi",
    val debugSummary: String = "",
    val capturedAt: Long = System.currentTimeMillis(),
    val rawText: String = ""
)
