package com.homeprojects.carlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

data class Telemetry(
    val speedKmh: Int? = null,      // null until the first fix
    val bearing: Float? = null,     // kept from the last fix fast enough to have one
    val tripStartAt: Long? = null,  // elapsedRealtime of the trip's first fix
    val distanceM: Double = 0.0,
    val lastFixAt: Long = 0L,       // elapsedRealtime
    val lat: Double? = null,
    val lon: Double? = null,
) {
    fun hasFix(now: Long = SystemClock.elapsedRealtime()) = lastFixAt > 0 && now - lastFixAt < TripMath.FIX_STALE_MS
}

/**
 * GPS speedometer and trip computer. Updates every second from GPS_PROVIDER.
 * ponytail: listens from MainActivity, so while another app is in front Android may throttle the
 * fixes and the trip distance undercounts; move it into a foreground service if that shows.
 */
object TelemetryManager : LocationListener {
    val state = MutableStateFlow(Telemetry())
    private var last: Location? = null
    private var manager: LocationManager? = null

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (manager != null || !hasPermission(context)) return
        val m = context.getSystemService(LocationManager::class.java) ?: return
        if (!m.allProviders.contains(LocationManager.GPS_PROVIDER)) return
        m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
        manager = m
        // Weather and the place name can start from the last known position before the first fix.
        m.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
            state.value = state.value.copy(lat = it.latitude, lon = it.longitude)
        }
    }

    fun stop() {
        manager?.removeUpdates(this)
        manager = null
    }

    fun resetTrip() {
        state.value = state.value.copy(tripStartAt = null, distanceM = 0.0)
    }

    override fun onLocationChanged(loc: Location) {
        val now = SystemClock.elapsedRealtime()
        val s = state.value
        val prev = last
        val speedKmh = when {
            loc.hasSpeed() -> loc.speed * 3.6f
            prev != null -> TripMath.speedFrom(prev.distanceTo(loc), loc.time - prev.time)
            else -> 0f
        }
        val parked = s.lastFixAt > 0 && now - s.lastFixAt > TripMath.PARKED_RESET_MS
        var distance = if (parked) 0.0 else s.distanceM
        if (prev != null && !parked) {
            val segment = prev.distanceTo(loc)
            if (TripMath.countSegment(segment, loc.accuracy, speedKmh)) distance += segment
        }
        val bearing = if (loc.hasBearing() && speedKmh >= TripMath.MIN_BEARING_KMH) loc.bearing else s.bearing
        state.value = Telemetry(
            speedKmh = speedKmh.toInt(),
            bearing = bearing,
            tripStartAt = if (parked || s.tripStartAt == null) now else s.tripStartAt,
            distanceM = distance,
            lastFixAt = now,
            lat = loc.latitude,
            lon = loc.longitude,
        )
        last = loc
    }

    // Abstract on Android 8-10; without these the system call crashes there (AbstractMethodError).
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}

/** The trip computer's arithmetic and formatting, free of Android types so it can be unit tested. */
object TripMath {
    const val FIX_STALE_MS = 5_000L
    /** No fix for this long means the car was parked: the next fix starts a new trip. */
    const val PARKED_RESET_MS = 30 * 60_000L
    const val MIN_BEARING_KMH = 3f
    private const val MAX_ACCURACY_M = 25f
    private const val MIN_MOVING_KMH = 3f
    private const val JUMP_M = 30f

    private val POINTS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    /** 8-point compass and degrees: "N / 015°". */
    fun heading(bearing: Float): String {
        val deg = ((bearing % 360f) + 360f) % 360f
        val point = POINTS[((deg + 22.5f) / 45f).toInt() % 8]
        return String.format(Locale.ROOT, "%s / %03d°", point, deg.toInt())
    }

    /** GPS jitter while standing still must not add distance: count a segment only when the fix is
     *  accurate and the car is moving, or when it has clearly moved. */
    fun countSegment(segmentM: Float, accuracyM: Float, speedKmh: Float): Boolean =
        accuracyM in 0f..MAX_ACCURACY_M && (speedKmh >= MIN_MOVING_KMH || segmentM >= JUMP_M)

    fun speedFrom(distanceM: Float, dtMs: Long): Float = if (dtMs <= 0) 0f else distanceM / (dtMs / 1000f) * 3.6f

    /** "00:42:18" */
    fun tripTime(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    }

    /** "24.8 KM", "850 M" under a kilometre. */
    fun distance(m: Double): String =
        if (m < 1000) String.format(Locale.ROOT, "%d M", m.toInt())
        else String.format(Locale.ROOT, "%.1f KM", m / 1000)
}
