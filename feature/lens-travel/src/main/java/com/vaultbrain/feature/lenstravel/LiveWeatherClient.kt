package com.vaultbrain.feature.lenstravel

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class DestinationWeather(
    val temperatureCelsius: Double,
    val conditionText: String,
    val conditionIcon: String,
    val humidity: Int,
    val windSpeedKmh: Double,
    val packingAdvice: String
)

object LiveWeatherClient {
    suspend fun fetchWeather(latitude: Double, longitude: Double): DestinationWeather = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "Nemory-App/1.0")

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                val current = root.optJSONObject("current")
                if (current != null) {
                    val temp = current.optDouble("temperature_2m", 18.0)
                    val code = current.optInt("weather_code", 0)
                    val humidity = current.optInt("relative_humidity_2m", 55)
                    val wind = current.optDouble("wind_speed_10m", 10.0)

                    val (condition, icon, advice) = parseWeatherCode(code, temp)
                    return@withContext DestinationWeather(
                        temperatureCelsius = temp,
                        conditionText = condition,
                        conditionIcon = icon,
                        humidity = humidity,
                        windSpeedKmh = wind,
                        packingAdvice = advice
                    )
                }
            }
        } catch (_: Exception) {
            // Graceful fallback below
        }

        fallbackWeather()
    }

    private fun fallbackWeather(): DestinationWeather {
        return DestinationWeather(
            temperatureCelsius = 16.5,
            conditionText = "Partly Cloudy",
            conditionIcon = "cloud",
            humidity = 62,
            windSpeedKmh = 12.0,
            packingAdvice = "Mild weather • Light jacket recommended"
        )
    }

    private fun parseWeatherCode(code: Int, temp: Double): Triple<String, String, String> {
        return when (code) {
            0 -> {
                val advice = if (temp > 25) "Sunny & warm • Sunglasses & sunscreen needed" else "Clear skies • Great for walking"
                Triple("Clear Sky", "sunny", advice)
            }
            1, 2 -> Triple("Partly Cloudy", "partly_cloudy", "Pleasant weather • Light layers")
            3 -> Triple("Overcast", "cloudy", "Cool & overcast • Jacket recommended")
            45, 48 -> Triple("Foggy", "fog", "Low visibility • Plan extra commute time")
            in 51..67 -> Triple("Rain", "rainy", "Rain expected • Pack an umbrella & waterproof shoes")
            in 71..77 -> Triple("Snow", "snowy", "Snow forecast • Thermal wear & winter boots")
            in 95..99 -> Triple("Thunderstorm", "thunderstorm", "Severe storms • Check airport delay status")
            else -> Triple("Mild", "partly_cloudy", "Comfortable conditions")
        }
    }
}

object SkyscannerDeepLinker {
    fun launchFlightSearch(context: Context, origin: String, destination: String, departDateIso: String, returnDateIso: String? = null) {
        val path = buildString {
            append("https://www.skyscanner.com/transport/flights/")
            append(origin.trim().uppercase())
            append("/")
            append(destination.trim().uppercase())
            append("/")
            append(departDateIso)
            append("/")
            if (!returnDateIso.isNullOrBlank()) {
                append(returnDateIso)
                append("/")
            }
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(path))
        try {
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    fun launchEverywhere(context: Context, origin: String) {
        val path = "https://www.skyscanner.com/transport/flights-from/${origin.trim().uppercase()}/"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(path))
        try {
            context.startActivity(intent)
        } catch (_: Exception) { }
    }
}
