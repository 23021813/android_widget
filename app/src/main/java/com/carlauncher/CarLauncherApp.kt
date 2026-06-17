package com.carlauncher

import android.app.Application

class CarLauncherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize any global resources here
        instance = this
    }

    companion object {
        lateinit var instance: CarLauncherApp
            private set
    }
}
