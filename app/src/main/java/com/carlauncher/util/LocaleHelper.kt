package com.carlauncher.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {

    /**
     * Applies the given locale to the given Context.
     * Should be called from Activity.attachBaseContext() and also
     * when the language setting changes (triggers Activity.recreate()).
     */
    fun applyLocale(context: Context, localeCode: String): Context {
        val effectiveCode = if (localeCode.isEmpty()) {
            if (Locale.getDefault().language == "vi") "vi" else "en"
        } else {
            localeCode
        }

        val locale = Locale(effectiveCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }
}
