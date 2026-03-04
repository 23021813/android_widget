package com.carlauncher.service

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast

object SplitScreenLauncher {

    /**
     * Launch two apps in split-screen mode.
     *
     * @param actionIntent1 Optional override intent for app1 (e.g. navigation URI).
     *                      If null, uses the default launch intent for package1.
     * @param actionIntent2 Optional override intent for app2 (e.g. music search URI).
     *                      If null, uses the default launch intent for package2.
     */
    fun launchSplitScreen(
        context: Context,
        package1: String,
        package2: String,
        actionIntent1: Intent? = null,
        actionIntent2: Intent? = null
    ) {
        val pm = context.packageManager

        // Luôn dùng launch intent mặc định để tạo split-screen,
        // actionIntent1/2 sẽ được gửi RIÊNG SAU KHI split đã hình thành
        val launchIntent1 = pm.getLaunchIntentForPackage(package1)
        val launchIntent2 = pm.getLaunchIntentForPackage(package2)

        Log.d(
            "SplitScreenLauncher",
            "launchSplitScreen: package1=$package1 package2=$package2 " +
                "launch1=${launchIntent1?.action} launchData1=${launchIntent1?.data} " +
                "launch2=${launchIntent2?.action} launchData2=${launchIntent2?.data} " +
                "action1=${actionIntent1?.action} actionData1=${actionIntent1?.data} " +
                "action2=${actionIntent2?.action} actionData2=${actionIntent2?.data}"
        )

        if (launchIntent1 == null || launchIntent2 == null) {
            Toast.makeText(context, "Không tìm thấy ứng dụng", Toast.LENGTH_SHORT).show()
            Log.w("SplitScreenLauncher", "Missing launch intent: intent1=$launchIntent1 intent2=$launchIntent2")
            return
        }

        try {
            launchIntent1.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }

            val options1 = ActivityOptions.makeBasic()
            try {
                val method = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                method.invoke(options1, 3) // WINDOWING_MODE_SPLIT_SCREEN_PRIMARY
            } catch (_: Exception) {}

            context.startActivity(launchIntent1, options1.toBundle())

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    launchIntent2.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                        addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    }

                    val options2 = ActivityOptions.makeBasic()
                    try {
                        val method = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                        method.invoke(options2, 4) // WINDOWING_MODE_SPLIT_SCREEN_SECONDARY
                    } catch (_: Exception) {}

                    context.startActivity(launchIntent2, options2.toBundle())
                } catch (e: Exception) {
                    e.printStackTrace()
                    launchIntent2.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(launchIntent2)
                }
            }, 1200)

            // Sau khi split đã thiết lập, gửi thêm action-intents (navigation/search)
            if (actionIntent1 != null) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val intent = Intent(actionIntent1).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        Log.d("SplitScreenLauncher", "Sending post-split actionIntent1: $intent")
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("SplitScreenLauncher", "Failed to send actionIntent1 after split", e)
                    }
                }, 2200)
            }

            if (actionIntent2 != null) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val intent = Intent(actionIntent2).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        Log.d("SplitScreenLauncher", "Sending post-split actionIntent2: $intent")
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("SplitScreenLauncher", "Failed to send actionIntent2 after split", e)
                    }
                }, 2600)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Lỗi khởi chạy split-screen", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Build an action intent for Google Maps navigation.
     * Returns null if address is blank.
     */
    fun buildNavigationIntent(address: String): Intent? {
        if (address.isBlank()) return null
        val gmmUri = Uri.parse("google.navigation:q=${Uri.encode(address)}&mode=d")
        return Intent(Intent.ACTION_VIEW, gmmUri).apply {
            setPackage("com.google.android.apps.maps")
        }
    }

    /**
     * Build an action intent for YouTube Music search.
     * Returns null if keyword is blank.
     */
    fun buildMusicSearchIntent(keyword: String): Intent? {
        if (keyword.isBlank()) return null
        val musicUri = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(keyword)}")
        return Intent(Intent.ACTION_VIEW, musicUri).apply {
            setPackage("com.google.android.apps.youtube.music")
        }
    }

    fun launchApp(context: Context, packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            intent?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }?.let {
                context.startActivity(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
