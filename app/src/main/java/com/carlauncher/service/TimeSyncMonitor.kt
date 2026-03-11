package com.carlauncher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

object TimeSyncMonitor {
    private const val TAG = "TimeSyncMonitor"
    
    @Volatile
    var hasSynced: Boolean = false
        private set

    private val monitorScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isChecking = false
    private var pendingRequest = false
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var timeReceiver: BroadcastReceiver? = null

    fun startMonitoring(context: Context) {
        if (hasSynced) return
        
        Log.d(TAG, "Starting time sync monitoring...")
        
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
            
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available, triggering time check...")
                triggerTimeCheck(context)
            }
        }
        
        try {
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
        
        timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                    Log.d(TAG, "System time changed, triggering time check...")
                    triggerTimeCheck(context)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        try {
            context.registerReceiver(timeReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register time receiver", e)
        }
        
        // Initial check
        triggerTimeCheck(context)
    }
    
    @Synchronized
    private fun triggerTimeCheck(context: Context) {
        if (hasSynced) return
        
        if (isChecking) {
            pendingRequest = true
            return
        }
        
        isChecking = true
        monitorScope.launch {
            try {
                performTimeCheckSequence(context)
            } finally {
                synchronized(this@TimeSyncMonitor) {
                    isChecking = false
                    if (pendingRequest && !hasSynced) {
                        pendingRequest = false
                        triggerTimeCheck(context)
                    }
                }
            }
        }
    }

    private suspend fun performTimeCheckSequence(context: Context) {
        // Retry logic: 3 attempts if network is available but check fails
        repeat(3) { attempt ->
            if (hasSynced) return@repeat
            
            if (isTimeAccurate()) {
                synchronized(this@TimeSyncMonitor) {
                    if (hasSynced) return@synchronized
                    hasSynced = true
                }
                
                Log.d(TAG, "Time successfully validated via network!")
                stopMonitoring(context)
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Syncing and triggering missed schedules...")
                    ScheduleManager.syncAlarms(context)
                    ScheduleManager.checkAndTriggerMissedSchedules(context, skipSplitScreen = false)
                }
                return@performTimeCheckSequence
            }
            
            if (attempt < 2) {
                Log.d(TAG, "Time check attempt ${attempt + 1} failed, retrying in 3s...")
                delay(3000L)
            }
        }
    }
    
    fun stopMonitoring(context: Context) {
        Log.d(TAG, "Stopping time sync monitoring...")
        pendingRequest = false
        
        try {
            networkCallback?.let {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
        networkCallback = null
        
        try {
            timeReceiver?.let {
                context.unregisterReceiver(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering time receiver", e)
        }
        timeReceiver = null
    }

    private suspend fun isTimeAccurate(): Boolean {
        // Quick Year check (Fast path)
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        if (currentYear < 2024) {
            Log.w(TAG, "System year is $currentYear, which is invalid. Waiting for NTP sync.")
            return false
        }
        
        return withContext(Dispatchers.IO) {
            var urlConnection: HttpURLConnection? = null
            try {
                // Use a URL that returns headers quickly
                val url = URL("https://www.google.com")
                urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.requestMethod = "HEAD"
                urlConnection.connectTimeout = 5000
                urlConnection.readTimeout = 5000
                urlConnection.instanceFollowRedirects = true
                
                val dateStr = urlConnection.getHeaderField("Date")
                if (dateStr != null) {
                    val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                    val serverTime = format.parse(dateStr)?.time ?: return@withContext false
                    val deviceTime = System.currentTimeMillis()
                    
                    val difference = abs(serverTime - deviceTime)
                    Log.d(TAG, "Server time: $serverTime, Device time: $deviceTime, Diff: $difference ms")
                    
                    // Allow up to 2 minutes of difference
                    return@withContext difference < 120_000
                }
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check time via network: ${e.message}")
                false
            } finally {
                urlConnection?.disconnect()
            }
        }
    }
}
