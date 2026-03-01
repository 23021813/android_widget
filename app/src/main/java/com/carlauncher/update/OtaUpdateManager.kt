package com.carlauncher.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val forceUpdate: Boolean = false
)

object OtaUpdateManager {

    // ⚠️ Replace with your actual GitHub raw URL for version.json
    private const val VERSION_CHECK_URL =
        "https://raw.githubusercontent.com/23021813/android_widget/main/version.json"

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    private var progressJob: Job? = null

    /**
     * Check if there is a new version available.
     * Returns UpdateInfo if update available, null otherwise.
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(VERSION_CHECK_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val json = JSONObject(response)
            val remoteVersionCode = json.getInt("versionCode")
            val currentVersionCode = getCurrentVersionCode(context)

            if (remoteVersionCode > currentVersionCode) {
                UpdateInfo(
                    versionCode = remoteVersionCode,
                    versionName = json.getString("versionName"),
                    apkUrl = json.getString("downloadUrl"),
                    changelog = json.optString("changelog", ""),
                    forceUpdate = json.optBoolean("forceUpdate", false)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Download and install the APK using Android DownloadManager.
     */
    fun getDownloadedFile(context: Context, updateInfo: UpdateInfo): File? {
        val fileName = "CarFloat_${updateInfo.versionName}.apk"
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        // Only return if it's a reasonably sized APK (>1MB usually, but let's be safe with >0)
        return if (file.exists() && file.length() > 1024) file else null
    }

    fun clearCache(context: Context, updateInfo: UpdateInfo) {
        val file = getDownloadedFile(context, updateInfo)
        if (file?.exists() == true) {
            file.delete()
        }
    }

    fun downloadAndInstall(context: Context, updateInfo: UpdateInfo) {
        if (_isDownloading.value) return
        
        val fileName = "CarFloat_${updateInfo.versionName}.apk"
        val existingFile = getDownloadedFile(context, updateInfo)
        
        if (existingFile != null) {
            android.util.Log.d("OtaUpdateManager", "Installing existing file: ${existingFile.absolutePath}, size: ${existingFile.length()}")
            installApk(context, existingFile)
            return
        }
        
        _isDownloading.value = true
        _downloadProgress.value = 0f
        
        val request = DownloadManager.Request(Uri.parse(updateInfo.apkUrl))
            .setTitle("CarFloat Update ${updateInfo.versionName}")
            .setDescription(updateInfo.changelog.take(100))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        // Start progress polling
        progressJob?.cancel()
        progressJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && _isDownloading.value) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    
                    if (statusIndex != -1 && bytesDownloadedIndex != -1 && bytesTotalIndex != -1) {
                        val status = cursor.getInt(statusIndex)
                        val downloaded = cursor.getLong(bytesDownloadedIndex)
                        val total = cursor.getLong(bytesTotalIndex)
                        
                        if (total > 0) {
                            _downloadProgress.value = downloaded.toFloat() / total.toFloat()
                        }
                        
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            _isDownloading.value = false
                            _downloadProgress.value = 1f
                            cursor.close()
                            
                            // Get URI from DownloadManager (more reliable for some devices)
                            val uri = downloadManager.getUriForDownloadedFile(downloadId)
                            if (uri != null) {
                                installApkFromUri(context, uri)
                            } else {
                                // Fallback to file path
                                val downloadedFile = File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                    fileName
                                )
                                installApk(context, downloadedFile)
                            }
                            break
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            _isDownloading.value = false
                            cursor.close()
                            break
                        }
                    }
                }
                cursor?.close()
                delay(500)
            }
        }

        // BroadcastReceiver remains as secondary/fallback notification
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    ctx.unregisterReceiver(this)
                    // Polling usually catches it first, but just in case:
                    if (_isDownloading.value) {
                        _isDownloading.value = false
                        _downloadProgress.value = 1f
                        val uri = downloadManager.getUriForDownloadedFile(downloadId)
                        if (uri != null) {
                            installApkFromUri(ctx, uri)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    private fun installApkFromUri(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Install failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getCurrentVersionCode(context: Context): Int {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        } catch (e: Exception) {
            0
        }
    }
}
