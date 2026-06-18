package com.carlauncher

import android.app.Application
import com.carlauncher.service.AppContextHolder

class CarLauncherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize any global resources here
        instance = this
        AppContextHolder.appContext = applicationContext
    }

    companion object {
        lateinit var instance: CarLauncherApp
            private set
    }
}
