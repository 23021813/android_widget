package com.carlauncher.service.voice

/**
 * Parses recognized speech text into actionable commands.
 * Supports Vietnamese and English patterns.
 */
object VoiceCommandParser {

    data class ParsedCommand(
        val type: CommandType,
        val parameter: String
    )

    enum class CommandType {
        NAVIGATE, PLAY_MUSIC, OPEN_VIDEO, UNKNOWN
    }

    // Patterns ordered from most specific to least specific
    private val navigationPatterns = listOf(
        // Vietnamese
        Regex("(?:dẫn đường|chỉ đường|đưa tôi|đi|tới|đến|navigate|directions|take me|go)\\s*(?:đến|tới|to)?\\s+(.+)", RegexOption.IGNORE_CASE)
    )

    private val musicPatterns = listOf(
        // Vietnamese
        Regex("(?:tìm bài hát|phát nhạc|nghe bài|mở bài|bật nhạc|tìm nhạc)\\s+(.+)", RegexOption.IGNORE_CASE),
        // English
        Regex("(?:play song|find song|play music|search music|play)\\s+(.+)", RegexOption.IGNORE_CASE)
    )

    private val videoPatterns = listOf(
        // Vietnamese
        Regex("(?:mở video|xem video|phát video|bật video)\\s+(.+)", RegexOption.IGNORE_CASE),
        // English
        Regex("(?:open video|play video|watch video|watch)\\s+(.+)", RegexOption.IGNORE_CASE)
    )

    fun parse(text: String): ParsedCommand {
        val normalized = text.trim()

        // Check video patterns FIRST (more specific: "mở video X" vs "mở X")
        for (pattern in videoPatterns) {
            pattern.find(normalized)?.let {
                val param = it.groupValues[1].trim()
                if (param.isNotEmpty()) {
                    return ParsedCommand(CommandType.OPEN_VIDEO, param)
                }
            }
        }

        // Check music patterns
        for (pattern in musicPatterns) {
            pattern.find(normalized)?.let {
                val param = it.groupValues[1].trim()
                if (param.isNotEmpty()) {
                    return ParsedCommand(CommandType.PLAY_MUSIC, param)
                }
            }
        }

        // Check navigation patterns (broadest match)
        for (pattern in navigationPatterns) {
            pattern.find(normalized)?.let {
                val param = it.groupValues[1].trim()
                if (param.isNotEmpty()) {
                    return ParsedCommand(CommandType.NAVIGATE, param)
                }
            }
        }

        return ParsedCommand(CommandType.UNKNOWN, normalized)
    }
}
