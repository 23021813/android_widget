package com.carlauncher

import android.app.Activity
import android.os.Bundle
import com.carlauncher.service.SplitScreenLauncher

class SplitScreenProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val pkg1 = intent.getStringExtra("pkg1")
        val pkg2 = intent.getStringExtra("pkg2")
        
        if (pkg1 != null && pkg2 != null) {
            SplitScreenLauncher.launchSplitScreen(this, pkg1, pkg2)
        }
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            finish()
        }, 1500)
    }
}
