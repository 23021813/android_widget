package com.carlauncher.service

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.widget.Toast

object SplitScreenLauncher {

    /**
     * Launch two apps in split-screen mode.
     * 
     * Strategy:
     * 1. Launch app1 in WINDOWING_MODE_SPLIT_SCREEN_PRIMARY (3) via reflection
     * 2. Launch app2 adjacent in WINDOWING_MODE_SPLIT_SCREEN_SECONDARY (4) via reflection
     */
    fun launchSplitScreen(context: Context, package1: String, package2: String) {
        val pm = context.packageManager

        val intent1 = pm.getLaunchIntentForPackage(package1)
        val intent2 = pm.getLaunchIntentForPackage(package2)

        if (intent1 == null || intent2 == null) {
            Toast.makeText(context, "Không tìm thấy ứng dụng", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Step 1: Launch first app in a new task as SPLIT_SCREEN_PRIMARY
            intent1.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }

            val options1 = ActivityOptions.makeBasic()
            try {
                // 3 = WINDOWING_MODE_SPLIT_SCREEN_PRIMARY
                val method = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                method.invoke(options1, 3)
            } catch (e: Exception) {
                // Ignore, try to launch anyway
            }
            
            context.startActivity(intent1, options1.toBundle())

            // Step 2: After a moderate delay, launch second app adjacent
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    intent2.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                        addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    }

                    val options2 = ActivityOptions.makeBasic()
                    try {
                        // 4 = WINDOWING_MODE_SPLIT_SCREEN_SECONDARY
                        val method = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                        method.invoke(options2, 4)
                    } catch (e: Exception) {
                        // Ignore
                    }
                    
                    context.startActivity(intent2, options2.toBundle())
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fallback: just launch app2 normally
                    intent2.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent2)
                }
            }, 1200) // Increased to 1200ms to allow heavy car units to settle the primary app

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Lỗi khởi chạy split-screen", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Launch a single app.
     */
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
