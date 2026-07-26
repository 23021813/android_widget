package com.carlauncher.ui.screens

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.app.Activity

class WifiPopupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            val panelAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Settings.Panel.ACTION_WIFI
            } else {
                Settings.ACTION_WIFI_SETTINGS
            }
            
            val intent = Intent(panelAction).apply {
                // Do not use NEW_TASK flag when starting from an Activity context,
                // as we want the Panel to pop up over THIS activity.
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val fallbackIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(fallbackIntent)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
        
        // Finish immediately so the transparent activity is removed from the backstack
        finish()
    }
}
