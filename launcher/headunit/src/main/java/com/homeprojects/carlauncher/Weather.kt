package com.homeprojects.carlauncher

import android.content.Context
import android.location.Geocoder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

/** Cruising-mode header: place, temperature and "NIGHT / CLEAR". */
data class Weather(
    val place: String = "Jakarta, ID",
    val tempC: Int? = null,
    val label: String = "",
    val isDay: Boolean? = null,
    /** CLEAR, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM, FAIR; each skin words it its own way. */
    val condition: String = "",
)

/** Open-Meteo (no key) for the weather, Android's Geocoder for the place name; every 30 minutes. */
object WeatherRepo {
    private const val TAG = "CarHome.Weather"
    private const val REFRESH_MS = 30 * 60_000L
    // Until the first GPS fix.
    private const val JAKARTA_LAT = -6.2088
    private const val JAKARTA_LON = 106.8456

    val state = MutableStateFlow(Weather())
    @Volatile private var lastAt = 0L
    @Volatile private var running = false

    fun refreshIfDue(context: Context, lat: Double?, lon: Double?) {
        val now = SystemClock.elapsedRealtime()
        if (running || (lastAt != 0L && now - lastAt < REFRESH_MS)) return
        running = true
        val app = context.applicationContext
        thread(name = "weather") {
            try {
                val la = lat ?: JAKARTA_LAT
                val lo = lon ?: JAKARTA_LON
                val json = fetch(
                    String.format(
                        Locale.ROOT,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                            "&current=temperature_2m,weather_code,is_day&timezone=auto",
                        la, lo,
                    )
                )
                val current = json.getJSONObject("current")
                val place = if (lat != null && lon != null) placeName(app, la, lo) else null
                val day = current.optInt("is_day", 1) == 1
                state.value = Weather(
                    place = place ?: state.value.place,
                    tempC = Math.round(current.getDouble("temperature_2m")).toInt(),
                    label = (if (day) "DAY" else "NIGHT") + " / " + condition(current.optInt("weather_code", 0)),
                    isDay = day,
                    condition = condition(current.optInt("weather_code", 0)),
                )
                lastAt = SystemClock.elapsedRealtime()
            } catch (e: Exception) {
                Log.w(TAG, "refresh failed: $e")
            } finally {
                running = false
            }
        }
    }

    @Suppress("DEPRECATION")   // the async variant is Android 13+ only
    private fun placeName(context: Context, lat: Double, lon: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        val a = Geocoder(context, Locale.US).getFromLocation(lat, lon, 1)?.firstOrNull() ?: return null
        val area = a.subAdminArea ?: a.locality ?: a.adminArea ?: return null
        listOfNotNull(area, a.countryCode).joinToString(", ")
    }.getOrNull()

    private fun condition(code: Int) = when (code) {
        0, 1 -> "CLEAR"
        2, 3 -> "CLOUDY"
        45, 48 -> "FOG"
        in 51..57 -> "DRIZZLE"
        in 61..67, in 80..82 -> "RAIN"
        in 71..77, 85, 86 -> "SNOW"
        in 95..99 -> "STORM"
        else -> "FAIR"
    }

    private fun fetch(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 6000
            c.readTimeout = 6000
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }
}
