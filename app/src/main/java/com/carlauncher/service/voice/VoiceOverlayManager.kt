package com.carlauncher.service.voice

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.carlauncher.R
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.*
import kotlinx.coroutines.*

/**
 * Manages the floating voice command overlay.
 * Shows a glassmorphism UI with mic animation, real-time speech text,
 * and auto-executes parsed commands.
 */
class VoiceOverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceOverlayManager"
        private const val TIMEOUT_MS = 12000L // 12 seconds max listening
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var timeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Compose state holders
    private val _partialText = mutableStateOf("")
    private val _finalText = mutableStateOf("")
    private val _isListening = mutableStateOf(false)
    private val _statusText = mutableStateOf("Đang nghe...")
    private val _commandResult = mutableStateOf("")

    val isShowing: Boolean get() = overlayView != null

    fun show() {
        if (overlayView != null) {
            Log.d(TAG, "Overlay already showing")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition not available on this device")
            android.widget.Toast.makeText(context, context.getString(R.string.voice_speech_not_available), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.widget.Toast.makeText(context, context.getString(R.string.perm_mic_settings), android.widget.Toast.LENGTH_LONG).show()
            return
        }

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // Reset state
        _partialText.value = ""
        _finalText.value = ""
        _isListening.value = true
        _statusText.value = "🎙️ " + context.getString(R.string.voice_listening)
        _commandResult.value = ""

        val composeView = ComposeView(context).apply {
            val lifecycleOwner = VoiceOverlayLifecycleOwner()
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                VoiceOverlayContent(
                    statusText = _statusText.value,
                    partialText = _partialText.value,
                    finalText = _finalText.value,
                    isListening = _isListening.value,
                    commandResult = _commandResult.value,
                    onDismiss = { dismiss() }
                )
            }
        }

        overlayView = composeView
        try {
            windowManager?.addView(composeView, params)
            startListening()
            startTimeout()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show voice overlay", e)
            overlayView = null
        }
    }

    fun dismiss() {
        stopListening()
        timeoutJob?.cancel()
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
        _isListening.value = false
    }

    private fun startListening() {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                    _statusText.value = "🎙️ " + context.getString(R.string.voice_listening)
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started")
                    _statusText.value = "🎙️ " + context.getString(R.string.voice_listening)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Could animate mic icon based on volume
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "Speech ended")
                    _statusText.value = "⏳ " + context.getString(R.string.voice_processing)
                    _isListening.value = false
                }

                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> context.getString(R.string.voice_error_no_match)
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> context.getString(R.string.voice_error_timeout)
                        SpeechRecognizer.ERROR_AUDIO -> context.getString(R.string.voice_error_audio)
                        SpeechRecognizer.ERROR_NETWORK -> context.getString(R.string.voice_error_network)
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> context.getString(R.string.voice_error_network_timeout)
                        SpeechRecognizer.ERROR_CLIENT -> context.getString(R.string.voice_error_client)
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> context.getString(R.string.voice_error_permissions)
                        else -> context.getString(R.string.voice_error_generic, error)
                    }
                    Log.e(TAG, "Speech error: $errorMsg ($error)")
                    _statusText.value = "❌ $errorMsg"
                    _isListening.value = false

                    // Auto-dismiss after showing error
                    scope.launch {
                        delay(2000L)
                        dismiss()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    Log.d(TAG, "Final result: $text")

                    _finalText.value = text
                    _partialText.value = ""

                    if (text.isNotBlank()) {
                        processCommand(text)
                    } else {
                        _statusText.value = "❌ " + context.getString(R.string.voice_error_no_match)
                        scope.launch {
                            delay(2000L)
                            dismiss()
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        _partialText.value = text
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN") // Primary: Vietnamese
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            }

            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer", e)
            _statusText.value = "❌ " + context.getString(R.string.voice_error_init_failed)
            scope.launch {
                delay(2000L)
                dismiss()
            }
        }
    }

    private fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    private fun startTimeout() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(TIMEOUT_MS)
            if (overlayView != null && _finalText.value.isBlank()) {
                _statusText.value = "⏰ " + context.getString(R.string.voice_error_timeout)
                delay(1500L)
                dismiss()
            }
        }
    }

    private fun processCommand(text: String) {
        val command = VoiceCommandParser.parse(text)

        val resultText = when (command.type) {
            VoiceCommandParser.CommandType.NAVIGATE ->
                "🗺️ " + context.getString(R.string.voice_nav_result, command.parameter)
            VoiceCommandParser.CommandType.PLAY_MUSIC ->
                "🎵 " + context.getString(R.string.voice_music_result, command.parameter)
            VoiceCommandParser.CommandType.OPEN_VIDEO ->
                "▶️ " + context.getString(R.string.voice_video_result, command.parameter)
            VoiceCommandParser.CommandType.UNKNOWN ->
                "❓ " + context.getString(R.string.voice_unknown_param, command.parameter)
        }

        _commandResult.value = resultText
        _statusText.value = if (command.type != VoiceCommandParser.CommandType.UNKNOWN)
            "✅ " + context.getString(R.string.voice_cmd_received) else "❌ " + context.getString(R.string.voice_cmd_unrecognized)

        // Execute and dismiss
        scope.launch {
            delay(1000L) // Brief pause to show result
            if (command.type != VoiceCommandParser.CommandType.UNKNOWN) {
                VoiceCommandExecutor.execute(context, command)
            }
            delay(500L)
            dismiss()
        }
    }
}

// ═══════════════════════════════════════
// Compose UI for Voice Overlay
// ═══════════════════════════════════════

@Composable
private fun VoiceOverlayContent(
    statusText: String,
    partialText: String,
    finalText: String,
    isListening: Boolean,
    commandResult: String,
    onDismiss: () -> Unit
) {
    // Pulsating animation for mic
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Main card
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF161B22).copy(alpha = 0.95f),
                            Color(0xFF0D1117).copy(alpha = 0.95f)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Status text
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Mic icon with pulse animation
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulse ring (only when listening)
                    if (isListening) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = pulseAlpha * 0.3f))
                        )
                    }

                    // Mic circle
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) Color(0xFF00E5FF).copy(alpha = 0.3f)
                                else Color(0xFF333333)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Microphone",
                            tint = if (isListening) Color(0xFF00E5FF) else Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Recognized text (partial or final)
                val displayText = finalText.ifBlank { partialText }
                if (displayText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "\"$displayText\"",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Command result
                if (commandResult.isNotBlank()) {
                    Text(
                        text = commandResult,
                        color = Color(0xFF00E5FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Dismiss button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { onDismiss() }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// Minimal lifecycle owner for ComposeView in overlay
private class VoiceOverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}
