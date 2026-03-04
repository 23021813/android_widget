package com.carlauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.carlauncher.service.SplitScreenLauncher

class SplitScreenProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg1 = intent.getStringExtra("pkg1")
        val pkg2 = intent.getStringExtra("pkg2")
        val navAddress = intent.getStringExtra("nav_address")
        val musicKeyword = intent.getStringExtra("music_keyword")

        Log.d(
            "SplitScreenProxy",
            "onCreate: pkg1=$pkg1 pkg2=$pkg2 navAddress=$navAddress musicKeyword=$musicKeyword"
        )

        if (pkg1 != null && pkg2 != null) {
            val actionIntent1 = resolveActionIntent(pkg1, navAddress, musicKeyword)
            val actionIntent2 = resolveActionIntent(pkg2, navAddress, musicKeyword)

            Log.d(
                "SplitScreenProxy",
                "Resolved actions: action1=${actionIntent1?.data} action2=${actionIntent2?.data}"
            )

            SplitScreenLauncher.launchSplitScreen(
                this, pkg1, pkg2,
                actionIntent1 = actionIntent1,
                actionIntent2 = actionIntent2
            )
        } else {
            Log.w("SplitScreenProxy", "Missing pkg1/pkg2 in intent, aborting split")
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            finish()
        }, 1500)
    }

    private fun resolveActionIntent(pkg: String, navAddress: String?, musicKeyword: String?): Intent? {
        if (pkg == "com.google.android.apps.maps" && !navAddress.isNullOrBlank()) {
            return SplitScreenLauncher.buildNavigationIntent(navAddress)
        }
        if (pkg == "com.google.android.apps.youtube.music" && !musicKeyword.isNullOrBlank()) {
            return SplitScreenLauncher.buildMusicSearchIntent(musicKeyword)
        }
        return null
    }
}
