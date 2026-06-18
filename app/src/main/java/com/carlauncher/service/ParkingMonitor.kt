package com.carlauncher.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.carlauncher.data.SettingsDataStore
import com.carlauncher.data.models.ParkingAlertConfig
import com.carlauncher.data.secrets.SecretsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Background watcher that decides when the car has been idle long enough to warrant
 * an alert. Lives in the OverlayService coroutine scope; calling [start] is idempotent.
 *
 * State machine (per tick):
 *   sample location -> if distance to lastSignificantLocation > threshold, reset
 *     "last movement" timestamp; otherwise accumulate idle time.
 *   When idle time crosses the configured threshold AND the cooldown has elapsed,
 *   dispatch an alert through the configured [ParkingNotifier] chain.
 */
class ParkingMonitor(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val secretsStore: SecretsStore,
    private val scope: CoroutineScope,
    private val locationProvider: () -> Location? = { defaultGetLastLocation() }
) {

    private var monitorJob: Job? = null
    private var configJob: Job? = null

    private val lastKnownSignificantLocation = arrayOfNulls<Location>(1)
    private val lastMovementMs = longArrayOf(0L)
    private val lastAlertMs = longArrayOf(0L)

    @Volatile
    private var activeConfig: ParkingAlertConfig = ParkingAlertConfig()
    @Volatile
    private var activeNotifier: ParkingNotifier? = null

    private var wasEnabled: Boolean = false

    fun start() {
        if (monitorJob?.isActive == true) return
        Log.d(TAG, "start()")
        current = this
        configJob = scope.launch {
            settingsDataStore.settingsFlow.collectLatest { settings ->
                applyConfig(settings.parkingAlert)
            }
        }
    }

    fun stop() {
        Log.d(TAG, "stop()")
        configJob?.cancel()
        configJob = null
        monitorJob?.cancel()
        monitorJob = null
        if (current === this) current = null
    }

    private suspend fun applyConfig(config: ParkingAlertConfig) {
        activeConfig = config
        val enabledNow = config.enabled

        if (enabledNow == wasEnabled) {
            // Idle / distance / cooldown / channel toggles changed but enable state unchanged.
            // Loop is already running (or already stopped) — just update the live config and
            // rebuild the notifier so the next tick uses the new values without a restart.
            if (enabledNow) {
                activeNotifier = ParkingNotifierFactory.build(context, config, secretsStore)
                Log.d(TAG, "applyConfig: in-place update (idle=${config.idleMinutes} dist=${config.distanceMeters} cooldown=${config.cooldownMinutes})")
            }
            return
        }

        // Enable state changed → tear down / spin up the loop exactly once.
        monitorJob?.cancel()
        monitorJob = null
        activeNotifier = null

        if (!enabledNow) {
            Log.d(TAG, "applyConfig: disabled, loop not started")
            wasEnabled = false
            return
        }

        if (config.lastMovementTimestamp > 0L) {
            lastMovementMs[0] = config.lastMovementTimestamp
            Log.d(TAG, "Restored lastMovementMs=${config.lastMovementTimestamp}")
        } else {
            lastMovementMs[0] = System.currentTimeMillis()
        }
        lastAlertMs[0] = config.lastAlertTimestamp
        lastKnownSignificantLocation[0] = null
        activeNotifier = ParkingNotifierFactory.build(context, config, secretsStore)

        monitorJob = scope.launch {
            runMonitorLoop(config)
        }
        wasEnabled = true
    }

    private suspend fun runMonitorLoop(@Suppress("UNUSED_PARAMETER") initialConfig: ParkingAlertConfig) {
        Log.d(TAG, "Monitor loop started: idle=${activeConfig.idleMinutes}min dist=${activeConfig.distanceMeters}m " +
            "cooldown=${activeConfig.cooldownMinutes}min channels=${activeNotifier?.channelName ?: "none"}")

        while (scope.isActive) {
            try {
                tick()
            } catch (e: Exception) {
                Log.e(TAG, "Tick failed", e)
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun tick() {
        val config = activeConfig
        val notifier = activeNotifier

        val loc = locationProvider.invoke()
        if (loc == null) {
            Log.d(TAG, "tick: no location available yet, skipping")
            return
        }
        val now = System.currentTimeMillis()

        val reference = lastKnownSignificantLocation[0]
        if (reference == null) {
            lastKnownSignificantLocation[0] = loc
            lastMovementMs[0] = now
            persistMovement(now)
            Log.d(TAG, "tick: baseline set at (${loc.latitude}, ${loc.longitude})")
            return
        }

        val distanceMeters = FloatArray(1)
        Location.distanceBetween(
            reference.latitude, reference.longitude,
            loc.latitude, loc.longitude,
            distanceMeters
        )
        val dist = distanceMeters[0]
        if (dist >= config.distanceMeters) {
            lastKnownSignificantLocation[0] = loc
            lastMovementMs[0] = now
            persistMovement(now)
            Log.d(TAG, "tick: moved ${dist.toInt()}m — reset idle timer")
            return
        }

        val idleMs = now - lastMovementMs[0]
        val idleMinutes = idleMs / 60_000L
        if (idleMinutes < config.idleMinutes) return

        val cooldownMs = config.cooldownMinutes.toLong() * 60_000L
        if (now - lastAlertMs[0] < cooldownMs) {
            Log.d(TAG, "tick: idle ${idleMinutes}min reached but still in cooldown (${(now - lastAlertMs[0]) / 1000}s left)")
            return
        }

        if (notifier == null) {
            Log.w(TAG, "tick: would alert (idle ${idleMinutes}min) but no notifier configured")
            return
        }

        val subject = "CarFloat Parking Alert"
        val body = buildAlertBody(loc, idleMinutes.toInt())
        val ok = notifier.send(subject, body)
        if (ok) {
            lastAlertMs[0] = now
            persistAlert(now)
            Log.i(TAG, "Alert dispatched via ${notifier.channelName}")
        } else {
            Log.w(TAG, "Alert dispatch failed, will retry next cycle")
        }
    }

    private fun buildAlertBody(loc: Location, idleMinutes: Int): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val mapLink = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
        return buildString {
            append("Xe đã đứng yên <b>${idleMinutes} phút</b> tại vị trí này.\n")
            append("Thời gian: $ts\n")
            append("Toạ độ: %.5f, %.5f\n".format(loc.latitude, loc.longitude))
            append("Độ chính xác: %.0f m\n".format(loc.accuracy))
            append("\nXem vị trí: <a href=\"$mapLink\">Mở Google Maps</a>")
        }
    }

    private suspend fun persistMovement(ts: Long) {
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                val current = settingsDataStore.settingsFlow.first()
                if (current.parkingAlert.lastMovementTimestamp == ts) return@withContext
                settingsDataStore.updateSettings(
                    current.copy(parkingAlert = current.parkingAlert.copy(lastMovementTimestamp = ts))
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "persistMovement failed", e)
        }
    }

    private suspend fun persistAlert(ts: Long) {
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                val current = settingsDataStore.settingsFlow.first()
                settingsDataStore.updateSettings(
                    current.copy(parkingAlert = current.parkingAlert.copy(lastAlertTimestamp = ts))
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "persistAlert failed", e)
        }
    }

    /**
     * Send a one-shot test alert through the user's configured channels. Used by the
     * "Test" button in Settings. Does NOT touch the cooldown or lastAlertMs state.
     */
    suspend fun sendTestAlert(): Boolean {
        val settings = settingsDataStore.settingsFlow.first()
        val notifier = ParkingNotifierFactory.build(context, settings.parkingAlert, secretsStore)
            ?: return false
        val loc = locationProvider.invoke()
        val body = buildString {
            append("Đây là tin nhắn thử nghiệm từ CarFloat.\n")
            if (loc != null) {
                append("Vị trí hiện tại: %.5f, %.5f\n".format(loc.latitude, loc.longitude))
                append("https://maps.google.com/?q=${loc.latitude},${loc.longitude}")
            } else {
                append("(Chưa có tín hiệu GPS)")
            }
        }
        return notifier.send("CarFloat Test Alert", body)
    }

    companion object {
        private const val TAG = "ParkingMonitor"
        private const val POLL_INTERVAL_MS = 60_000L

        @Volatile
        var current: ParkingMonitor? = null
            private set

        @SuppressLint("MissingPermission")
        private fun defaultGetLastLocation(): Location? {
            return try {
                val ctx = AppContextHolder.appContext ?: return null
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    return null
                }
                val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                )
                providers.mapNotNull { p ->
                    runCatching { lm.getLastKnownLocation(p) }.getOrNull()
                }.maxByOrNull { it.time }
            } catch (e: Exception) {
                Log.w(TAG, "defaultGetLastLocation failed", e)
                null
            }
        }
    }
}

/** Process-wide application context. Set once from CarLauncherApp.onCreate. */
object AppContextHolder {
    @Volatile
    var appContext: Context? = null
}
