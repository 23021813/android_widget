package com.carlauncher.bridge.core

object BlePayloadParser {
    fun parse(payload: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        if (payload.isBlank()) return map
        
        payload.lines().forEach { line ->
            val trimmed = line.trimEnd('\r', '\n') // Keep whitespace if any, but Android trims lines anyway. Actually, keep exact space as much as possible.
            // Let's accurately split by first '='
            if (trimmed.isNotEmpty()) {
                val splitIndex = trimmed.indexOf('=')
                if (splitIndex > 0) {
                    val key = trimmed.substring(0, splitIndex)
                    val value = trimmed.substring(splitIndex + 1)
                    map[key] = value
                }
            }
        }
        return map
    }
}
