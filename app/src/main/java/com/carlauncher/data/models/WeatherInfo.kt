package com.carlauncher.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class WeatherResponse(
    @Json(name = "main") val main: WeatherMain,
    @Json(name = "weather") val weather: List<WeatherCondition>,
    @Json(name = "name") val cityName: String
)

@JsonClass(generateAdapter = false)
data class WeatherMain(
    @Json(name = "temp") val temp: Double,
    @Json(name = "feels_like") val feelsLike: Double,
    @Json(name = "humidity") val humidity: Int
)

@JsonClass(generateAdapter = false)
data class WeatherCondition(
    @Json(name = "id") val id: Int,
    @Json(name = "main") val main: String,
    @Json(name = "description") val description: String,
    @Json(name = "icon") val icon: String
)

data class WeatherInfo(
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val condition: String,
    val iconCode: String,
    val cityName: String,
    val isCelsius: Boolean = true
) {
    val displayTemp: String
        get() = "${temperature.toInt()}°${if (isCelsius) "C" else "F"}"
}
