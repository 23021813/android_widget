package com.carlauncher.data

import com.carlauncher.data.models.TemperatureUnit
import com.carlauncher.data.models.WeatherInfo
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class WeatherRepository {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val api: WeatherApiService = Retrofit.Builder()
        .baseUrl(WeatherApiService.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(WeatherApiService::class.java)

    private var cachedWeather: WeatherInfo? = null
    private var lastFetchTime: Long = 0
    private val cacheValidMs = 10 * 60 * 1000L // 10 minutes

    suspend fun getWeatherByCity(
        city: String,
        apiKey: String,
        unit: TemperatureUnit = TemperatureUnit.CELSIUS
    ): WeatherInfo? {
        if (isCacheValid()) return cachedWeather

        return try {
            val units = if (unit == TemperatureUnit.CELSIUS) "metric" else "imperial"
            val response = api.getWeatherByCity(city, apiKey, units)
            val weather = WeatherInfo(
                temperature = response.main.temp,
                feelsLike = response.main.feelsLike,
                humidity = response.main.humidity,
                condition = response.weather.firstOrNull()?.description ?: "",
                iconCode = response.weather.firstOrNull()?.icon ?: "01d",
                cityName = response.cityName,
                isCelsius = unit == TemperatureUnit.CELSIUS
            )
            cachedWeather = weather
            lastFetchTime = System.currentTimeMillis()
            weather
        } catch (e: Exception) {
            cachedWeather // Return cached if available
        }
    }

    suspend fun getWeatherByLocation(
        lat: Double,
        lon: Double,
        apiKey: String,
        unit: TemperatureUnit = TemperatureUnit.CELSIUS
    ): WeatherInfo? {
        if (isCacheValid()) return cachedWeather

        return try {
            val units = if (unit == TemperatureUnit.CELSIUS) "metric" else "imperial"
            val response = api.getWeatherByLocation(lat, lon, apiKey, units)
            val weather = WeatherInfo(
                temperature = response.main.temp,
                feelsLike = response.main.feelsLike,
                humidity = response.main.humidity,
                condition = response.weather.firstOrNull()?.description ?: "",
                iconCode = response.weather.firstOrNull()?.icon ?: "01d",
                cityName = response.cityName,
                isCelsius = unit == TemperatureUnit.CELSIUS
            )
            cachedWeather = weather
            lastFetchTime = System.currentTimeMillis()
            weather
        } catch (e: Exception) {
            cachedWeather
        }
    }

    private fun isCacheValid(): Boolean {
        return cachedWeather != null &&
                (System.currentTimeMillis() - lastFetchTime) < cacheValidMs
    }
}
