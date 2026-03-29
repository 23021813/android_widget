package com.carlauncher.bridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.carlauncher.LauncherActivity
import com.carlauncher.R
import com.carlauncher.bridge.core.NavigationBridgeRepository
import com.carlauncher.bridge.core.SpeedSignTextAnalyzer
import com.carlauncher.bridge.model.SpeedSignDetectionResult
import com.carlauncher.data.SettingsDataStore
import com.carlauncher.data.models.SpeedSignCaptureSettings
import com.carlauncher.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SpeedSignCaptureService : Service() {
    companion object {
        private const val TAG = "SpeedSignCaptureService"
        private const val MIN_SPEED_LIMIT = 30
        private const val MAX_SPEED_LIMIT = 120
        const val CHANNEL_ID = "speed_sign_capture"
        const val NOTIFICATION_ID = 1304
        const val ACTION_START_CAPTURE = "com.carlauncher.speedsign.START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.carlauncher.speedsign.STOP_CAPTURE"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val analyzer = SpeedSignTextAnalyzer()

    private data class CapturedFrame(
        val bitmap: Bitmap,
        val timestampNs: Long
    )

    private data class ScreenSize(
        val width: Int,
        val height: Int
    )

    private var captureSettings = SpeedSignCaptureSettings()
    private var captureJob: kotlinx.coroutines.Job? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastPublishedPayload: String? = null
    private var consecutiveMisses = 0
    private var consecutiveNullFrames = 0
    private var consecutiveStaleFrames = 0
    private var lastFrameTimestampNs = Long.MIN_VALUE

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            AppLogger.w(TAG, "MediaProjection stopped by system")
            SpeedSignProjectionStore.clear()
            releaseProjectionResources()
            NavigationBridgeRepository.updateSpeedSignCaptureState(
                running = false,
                status = "projection_revoked"
            )
            updateNotification("Screen capture permission expired")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settingsDataStore = SettingsDataStore(this)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
        startForegroundCompat("Speed sign capture is preparing")
        observeSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_CAPTURE -> {
                stopCapture("disabled")
                stopSelf()
            }
            ACTION_START_CAPTURE -> {
                tryStartCapture()
            }
            else -> {
                if (captureSettings.enabled) {
                    tryStartCapture()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopCapture("stopped")
        analyzer.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsDataStore.settingsFlow
                .map { it.navigationBridge.speedSignCapture }
                .distinctUntilChanged()
                .collect { settings ->
                    captureSettings = settings
                    if (!settings.enabled) {
                        stopCapture("disabled")
                        stopSelf()
                        return@collect
                    }
                    tryStartCapture()
                }
        }
    }

    private fun tryStartCapture() {
        if (!captureSettings.enabled) {
            stopCapture("disabled")
            return
        }

        val projectionData = SpeedSignProjectionStore.get()
        if (projectionData == null) {
            AppLogger.w(TAG, "Capture waiting for MediaProjection permission")
            NavigationBridgeRepository.updateSpeedSignCaptureState(
                running = false,
                status = "permission_required"
            )
            updateNotification("Waiting for screen capture permission")
            return
        }

        if (!ensureProjection(projectionData.first, projectionData.second)) {
            AppLogger.e(TAG, "Failed to initialize MediaProjection")
            NavigationBridgeRepository.updateSpeedSignCaptureState(
                running = false,
                status = "projection_failed"
            )
            updateNotification("Cannot start screen capture")
            return
        }

        startCaptureLoop()
    }

    private fun ensureProjection(
        resultCode: Int,
        data: Intent,
        forceDisplayRefresh: Boolean = false
    ): Boolean {
        val activeProjection = projection
        if (activeProjection != null) {
            return if (forceDisplayRefresh || imageReader == null || virtualDisplay == null) {
                recreateCaptureSurfaces(activeProjection)
            } else {
                true
            }
        }

        val newProjection = runCatching {
            mediaProjectionManager.getMediaProjection(resultCode, data)
        }.getOrNull() ?: return false

        projection = newProjection
        newProjection.registerCallback(projectionCallback, null)
        return recreateCaptureSurfaces(newProjection)
    }

    private fun recreateCaptureSurfaces(activeProjection: MediaProjection): Boolean {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        runCatching { imageReader?.close() }
        imageReader = null

        val screenSize = getCurrentScreenSize()
        val width = screenSize.width.coerceAtLeast(1)
        val height = screenSize.height.coerceAtLeast(1)
        val density = resources.displayMetrics.densityDpi.coerceAtLeast(1)

        val newReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val newDisplay = activeProjection.createVirtualDisplay(
            "SpeedSignCaptureDisplay",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            newReader.surface,
            null,
            null
        )

        if (newDisplay == null) {
            runCatching { newReader.close() }
            return false
        }

        imageReader = newReader
        virtualDisplay = newDisplay
        return true
    }

    private fun startCaptureLoop() {
        captureJob?.cancel()
        captureJob = serviceScope.launch(Dispatchers.Default) {
            AppLogger.i(TAG, "Capture loop started. Interval=${captureSettings.intervalSeconds}s")
            NavigationBridgeRepository.updateSpeedSignCaptureState(running = true, status = "running")
            updateNotification("Speed sign capture is running")
            consecutiveNullFrames = 0
            consecutiveStaleFrames = 0
            lastFrameTimestampNs = Long.MIN_VALUE

            while (isActive && captureSettings.enabled) {
                val capturedFrame = captureFrameBitmap()
                if (capturedFrame == null) {
                    consecutiveNullFrames += 1
                    AppLogger.d(TAG, "No frame available from ImageReader")
                    NavigationBridgeRepository.updateSpeedSignCaptureState(
                        running = true,
                        status = "waiting_frame"
                    )

                    if (consecutiveNullFrames >= 3) {
                        AppLogger.w(TAG, "ImageReader stalled ($consecutiveNullFrames misses). Restarting capture loop")
                        NavigationBridgeRepository.updateSpeedSignCaptureState(
                            running = true,
                            status = "recovering"
                        )
                        scheduleCaptureLoopRecovery("frame_stall")
                        return@launch
                    }
                } else {
                    consecutiveNullFrames = 0
                    if (capturedFrame.timestampNs == lastFrameTimestampNs) {
                        consecutiveStaleFrames += 1
                    } else {
                        consecutiveStaleFrames = 0
                        lastFrameTimestampNs = capturedFrame.timestampNs
                    }

                    if (consecutiveStaleFrames >= 3) {
                        AppLogger.w(
                            TAG,
                            "Frame timestamp is stale ($consecutiveStaleFrames repeats). Restarting capture pipeline"
                        )
                        NavigationBridgeRepository.updateSpeedSignCaptureState(
                            running = true,
                            status = "recovering"
                        )
                        capturedFrame.bitmap.recycle()
                        scheduleCaptureLoopRecovery("stale_frame")
                        return@launch
                    }

                    val frame = capturedFrame.bitmap

                    // Keep pipeline strict: capture full frame -> crop ROI -> OCR on ROI only.
                    val roiBitmap = cropRoi(frame, captureSettings)
                    val roiDetection = runCatching {
                        analyzer.analyze(roiBitmap, sourceLabel = "roi")
                    }
                        .onFailure { AppLogger.e(TAG, "OCR analyze failed", it) }
                        .getOrNull()

                    val detection = roiDetection
                    val publishable = isPublishableDetection(detection)

                    if (publishable) {
                        consecutiveMisses = 0
                    } else {
                        consecutiveMisses += 1
                    }

                    if (detection != null) {
                        NavigationBridgeRepository.updateSpeedSignDetection(detection)
                    } else {
                        NavigationBridgeRepository.updateSpeedSignDetection(null)
                    }

                    if (publishable && detection != null) {
                        consecutiveMisses = 0
                        AppLogger.d(
                            TAG,
                            "Detection current=${detection.currentLimit ?: "-"}, " +
                                "upcoming=${detection.upcomingLimit ?: "-"}, " +
                                "distance=${detection.upcomingDistanceMeters ?: "-"}, " +
                                "layout=${detection.layoutType}, " +
                                "source=${detection.captureSource}, " +
                                "candidates=${detection.candidates.joinToString(",")}, " +
                                "summary=${detection.debugSummary}"
                        )
                        publishDetection(detection)
                        NavigationBridgeRepository.updateSpeedSignCaptureState(
                            running = true,
                            status = "running"
                        )
                    } else {
                        if (consecutiveMisses >= 1) {
                            publishNoDetection()
                        }
                        AppLogger.d(
                            TAG,
                            "No valid speed sign from OCR. " +
                                "source=${detection?.captureSource ?: "-"}, " +
                                "layout=${detection?.layoutType ?: "-"}, " +
                                "summary=${detection?.debugSummary ?: "none"}"
                        )
                        NavigationBridgeRepository.updateSpeedSignCaptureState(
                            running = true,
                            status = "no_signs"
                        )
                    }

                    if (roiBitmap !== frame) {
                        roiBitmap.recycle()
                    }
                    frame.recycle()
                }

                delay(captureSettings.intervalSeconds.coerceIn(2, 30) * 1000L)
            }
        }
    }

    private fun scheduleCaptureLoopRecovery(reason: String) {
        serviceScope.launch {
            AppLogger.i(TAG, "Scheduling capture loop recovery. reason=$reason")
            delay(120)
            if (!captureSettings.enabled) {
                return@launch
            }

            val projectionData = SpeedSignProjectionStore.get()
            if (projectionData == null) {
                NavigationBridgeRepository.updateSpeedSignCaptureState(
                    running = false,
                    status = "permission_required"
                )
                updateNotification("Waiting for screen capture permission")
                return@launch
            }

            val recovered = ensureProjection(
                resultCode = projectionData.first,
                data = projectionData.second,
                forceDisplayRefresh = true
            )

            if (!recovered) {
                NavigationBridgeRepository.updateSpeedSignCaptureState(
                    running = false,
                    status = "projection_failed"
                )
                updateNotification("Cannot recover screen capture")
                return@launch
            }

            startCaptureLoop()
        }
    }

    private fun captureFrameBitmap(): CapturedFrame? {
        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null

        return try {
            val plane = image.planes.firstOrNull() ?: return null
            val timestampNs = image.timestamp
            val buffer = plane.buffer
            val width = image.width
            val height = image.height
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            val paddedBitmap = Bitmap.createBitmap(
                width + (rowPadding / pixelStride),
                height,
                Bitmap.Config.ARGB_8888
            )
            paddedBitmap.copyPixelsFromBuffer(buffer)

            val exactBitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
            paddedBitmap.recycle()
            CapturedFrame(
                bitmap = exactBitmap,
                timestampNs = timestampNs
            )
        } catch (_: Exception) {
            null
        } finally {
            image.close()
        }
    }

    private fun cropRoi(source: Bitmap, roi: SpeedSignCaptureSettings): Bitmap {
        val minOcrSide = 32
        val requestedWidth = roi.roiWidth.coerceAtLeast(80).coerceAtMost(source.width)
        val requestedHeight = roi.roiHeight.coerceAtLeast(80).coerceAtMost(source.height)

        val width = requestedWidth.coerceAtLeast(minOf(minOcrSide, source.width))
        val height = requestedHeight.coerceAtLeast(minOf(minOcrSide, source.height))

        val maxX = (source.width - width).coerceAtLeast(0)
        val maxY = (source.height - height).coerceAtLeast(0)
        val x = roi.roiX.coerceIn(0, maxX)
        val y = roi.roiY.coerceIn(0, maxY)

        return runCatching {
            Bitmap.createBitmap(source, x, y, width, height)
        }.getOrElse {
            source
        }
    }

    private fun getCurrentScreenSize(): ScreenSize {
        val metrics = resources.displayMetrics
        return ScreenSize(
            width = metrics.widthPixels.coerceAtLeast(1),
            height = metrics.heightPixels.coerceAtLeast(1)
        )
    }

    private fun isPublishableDetection(result: SpeedSignDetectionResult?): Boolean {
        val current = result?.currentLimit ?: return false
        if (current !in MIN_SPEED_LIMIT..MAX_SPEED_LIMIT) {
            return false
        }

        val upcoming = result.upcomingLimit
        return upcoming == null || upcoming in MIN_SPEED_LIMIT..MAX_SPEED_LIMIT
    }

    private fun publishDetection(result: SpeedSignDetectionResult) {
        val signature = listOf(
            result.currentLimit?.toString().orEmpty(),
            result.upcomingLimit?.toString().orEmpty(),
            result.upcomingDistanceMeters?.toString().orEmpty()
        ).joinToString("|")

        if (signature == lastPublishedPayload) {
            AppLogger.d(TAG, "Skip speed sign update because payload is unchanged")
            return
        }
        lastPublishedPayload = signature

        AppLogger.i(
            TAG,
            "Publish speed signs current=${result.currentLimit ?: "-"}, " +
                "upcoming=${result.upcomingLimit ?: "-"}, " +
                "distance=${result.upcomingDistanceMeters ?: "-"}"
        )

        BridgeServiceController.sendSpeedSigns(
            context = applicationContext,
            currentLimit = result.currentLimit,
            upcomingLimit = result.upcomingLimit,
            distanceMeters = result.upcomingDistanceMeters
        )
    }

    private fun publishNoDetection() {
        val emptySignature = "||"
        if (lastPublishedPayload == emptySignature) {
            return
        }
        lastPublishedPayload = emptySignature
        AppLogger.i(TAG, "Publish speed signs clear-state update")
        BridgeServiceController.sendSpeedSigns(
            context = applicationContext,
            currentLimit = null,
            upcomingLimit = null,
            distanceMeters = null
        )
    }

    private fun stopCapture(status: String) {
        AppLogger.i(TAG, "Capture loop stopping. Status=$status")
        captureJob?.cancel()
        captureJob = null
        releaseProjectionResources()
        lastPublishedPayload = null
        consecutiveMisses = 0
        consecutiveNullFrames = 0
        consecutiveStaleFrames = 0
        lastFrameTimestampNs = Long.MIN_VALUE
        NavigationBridgeRepository.updateSpeedSignCaptureState(running = false, status = status)
        updateNotification("Speed sign capture stopped")
    }

    private fun releaseProjectionResources() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        runCatching { imageReader?.close() }
        imageReader = null

        projection?.let { mediaProjection ->
            runCatching { mediaProjection.unregisterCallback(projectionCallback) }
            runCatching { mediaProjection.stop() }
        }
        projection = null
    }

    private fun startForegroundCompat(contentText: String) {
        val notification = buildNotification(contentText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LauncherActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Speed Sign Capture")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Speed Sign Capture",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }
}
