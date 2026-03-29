package com.carlauncher.bridge.service

import android.content.Intent

object SpeedSignProjectionStore {
    private var projectionResultCode: Int? = null
    private var projectionData: Intent? = null

    @Synchronized
    fun update(resultCode: Int, data: Intent) {
        projectionResultCode = resultCode
        projectionData = Intent(data)
    }

    @Synchronized
    fun get(): Pair<Int, Intent>? {
        val code = projectionResultCode ?: return null
        val intent = projectionData ?: return null
        return code to Intent(intent)
    }

    @Synchronized
    fun hasProjection(): Boolean {
        return projectionResultCode != null && projectionData != null
    }

    @Synchronized
    fun clear() {
        projectionResultCode = null
        projectionData = null
    }
}
