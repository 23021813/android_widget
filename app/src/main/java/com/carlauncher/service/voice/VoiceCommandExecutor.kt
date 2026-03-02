package com.carlauncher.service.voice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast

/**
 * Executes parsed voice commands by launching the appropriate app/intent.
 */
object VoiceCommandExecutor {

    private const val TAG = "VoiceCommandExecutor"

    fun execute(context: Context, command: VoiceCommandParser.ParsedCommand) {
        Log.d(TAG, "Executing command: ${command.type} → '${command.parameter}'")

        when (command.type) {
            VoiceCommandParser.CommandType.NAVIGATE -> launchNavigation(context, command.parameter)
            VoiceCommandParser.CommandType.PLAY_MUSIC -> launchMusicSearch(context, command.parameter)
            VoiceCommandParser.CommandType.OPEN_VIDEO -> launchVideoSearch(context, command.parameter)
            VoiceCommandParser.CommandType.UNKNOWN -> {
                Toast.makeText(context, "❓ Không hiểu lệnh: ${command.parameter}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Open Google Maps with turn-by-turn navigation to the given address.
     * mode=d = driving mode (appropriate for car head unit)
     */
    private fun launchNavigation(context: Context, address: String) {
        try {
            val gmmUri = Uri.parse("google.navigation:q=${Uri.encode(address)}&mode=d")
            val intent = Intent(Intent.ACTION_VIEW, gmmUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Google Maps navigation launched for: $address")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Google Maps", e)
            // Fallback: open in browser
            try {
                val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(address)}&travelmode=driving")
                val browserIntent = Intent(Intent.ACTION_VIEW, browserUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Không thể mở Google Maps", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Open YouTube Music and search for the given keyword.
     */
    private fun launchMusicSearch(context: Context, keyword: String) {
        try {
            val musicUri = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(keyword)}")
            val intent = Intent(Intent.ACTION_VIEW, musicUri).apply {
                setPackage("com.google.android.apps.youtube.music")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "YouTube Music search launched for: $keyword")
        } catch (e: Exception) {
            Log.e(TAG, "YouTube Music not available, trying media search", e)
            try {
                // Fallback: MEDIA_PLAY_FROM_SEARCH (may auto-play on some devices)
                val fallbackIntent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(android.app.SearchManager.QUERY, keyword)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Không thể mở YouTube Music", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Open YouTube and search for the given video query.
     */
    private fun launchVideoSearch(context: Context, query: String) {
        try {
            val youtubeUri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            val intent = Intent(Intent.ACTION_VIEW, youtubeUri).apply {
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "YouTube search launched for: $query")
        } catch (e: Exception) {
            Log.e(TAG, "YouTube not available", e)
            // Fallback: open in browser
            try {
                val browserUri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
                val browserIntent = Intent(Intent.ACTION_VIEW, browserUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Không thể mở YouTube", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
