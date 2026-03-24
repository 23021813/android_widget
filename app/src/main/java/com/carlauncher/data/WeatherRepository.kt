package com.carlauncher.data

import android.util.Log
import com.carlauncher.data.models.TemperatureUnit
import com.carlauncher.data.models.WeatherInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class WeatherRepository {

    companion object {
        private const val TAG = "WeatherRepository"
        private const val WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private var cachedWeather: WeatherInfo? = null
    private var cachedRequestKey: String? = null
    private var lastFetchTime: Long = 0
    private val cacheValidMs = 10 * 60 * 1000L // 10 minutes

    suspend fun getWeatherByCity(
        city: String,
        apiKey: String,
        unit: TemperatureUnit = TemperatureUnit.CELSIUS
    ): WeatherInfo? {
        val normalizedCity = city.trim()
        val requestKey = "city:${normalizedCity.lowercase(Locale.ROOT)}:${unit.name}"
        if (isCacheValid(requestKey)) return cachedWeather

        val requestUrl = WEATHER_URL.toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", normalizedCity)
            .addQueryParameter("appid", apiKey.trim())
            .addQueryParameter("units", unitsFor(unit))
            .addQueryParameter("lang", "vi")
            .build()

        return executeWeatherRequest(requestUrl.toString(), requestKey, unit, "city: $normalizedCity")
    }

    suspend fun getWeatherByLocation(
        lat: Double,
        lon: Double,
        apiKey: String,
        unit: TemperatureUnit = TemperatureUnit.CELSIUS
    ): WeatherInfo? {
        val requestKey = String.format(Locale.US, "gps:%.3f:%.3f:%s", lat, lon, unit.name)
        if (isCacheValid(requestKey)) return cachedWeather

        val requestUrl = WEATHER_URL.toHttpUrl()
            .newBuilder()
            .addQueryParameter("lat", lat.toString())
            .addQueryParameter("lon", lon.toString())
            .addQueryParameter("appid", apiKey.trim())
            .addQueryParameter("units", unitsFor(unit))
            .addQueryParameter("lang", "vi")
            .build()

        return executeWeatherRequest(requestUrl.toString(), requestKey, unit, "location: lat=$lat lon=$lon")
    }

    private fun isCacheValid(requestKey: String): Boolean {
        return cachedWeather != null &&
                cachedRequestKey == requestKey &&
                (System.currentTimeMillis() - lastFetchTime) < cacheValidMs
    }

    private fun fallbackWeatherFor(requestKey: String): WeatherInfo? {
        return if (cachedRequestKey == requestKey) cachedWeather else null
    }

    private suspend fun executeWeatherRequest(
        url: String,
        requestKey: String,
        unit: TemperatureUnit,
        label: String
    ): WeatherInfo? {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return try {
            val weather = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Weather API error ${response.code}: $body")
                    }
                    parseWeatherResponse(body, unit)
                }
            }
            cachedWeather = weather
            cachedRequestKey = requestKey
            lastFetchTime = System.currentTimeMillis()
            weather
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch weather by $label", e)
            fallbackWeatherFor(requestKey)
        }
    }

    private fun parseWeatherResponse(body: String, unit: TemperatureUnit): WeatherInfo {
        val json = JSONObject(body)
        val main = json.getJSONObject("main")
        val weatherArray = json.optJSONArray("weather")
        val weather = if (weatherArray != null && weatherArray.length() > 0) {
            weatherArray.getJSONObject(0)
        } else {
            null
        }

        return WeatherInfo(
            temperature = main.getDouble("temp"),
            feelsLike = main.getDouble("feels_like"),
            humidity = main.getInt("humidity"),
            condition = weather?.optString("description").orEmpty(),
            iconCode = weather?.optString("icon").takeUnless { it.isNullOrBlank() } ?: "01d",
            cityName = json.optString("name"),
            isCelsius = unit == TemperatureUnit.CELSIUS
        )
    }

    private fun unitsFor(unit: TemperatureUnit): String {
        return if (unit == TemperatureUnit.CELSIUS) "metric" else "imperial"
    }
}
