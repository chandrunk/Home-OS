package com.homeprojects.carlauncher

import android.os.SystemClock
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.homeprojects.carlauncher.ui.HomeScreen
import com.homeprojects.carlauncher.ui.Overlay
import com.homeprojects.carlauncher.ui.Ui
import com.homeprojects.carlauncher.ui.theme.Night
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Renders every screen to PNG on the JVM (layoutlib), at the head unit's 1280 x 720 mdpi.
 * Record: ./gradlew :headunit:recordPaparazziDebug -> headunit/src/test/snapshots/images/
 */
class ScreensTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1280, screenHeight = 720, xdpi = 160, ydpi = 160,
            density = Density.MEDIUM, orientation = ScreenOrientation.LANDSCAPE,
        ),
        showSystemUi = false,
        useDeviceResolution = true,   // 1280 x 720 PNGs, 1:1 with the HTML design
    )

    @Before
    fun driving() {
        WeatherRepo.state.value = Weather("South Jakarta, ID", 31, "DAY / CLEAR", isDay = true)
        MediaHub.info.value = MediaInfo(
            packageName = MediaHub.YT_MUSIC, title = "Evaluasi", artist = "Hindia • Menari Dengan Bayangan",
            art = null, durationMs = 195_000, positionMs = 114_000, positionAt = 0, speed = 1f, playing = false,
        )
        CarNotificationListener.connected.value = true
        Ui.online = true
        Ui.wifi = true
        Ui.overlay = Overlay.None
        Night.on = false
        Ui.toast = null
        NavHub.state.value = NavInfo("250m", "TURN LEFT", "Jl. Jend. Sudirman (Semanggi)", 14)
    }

    /** layoutlib's elapsedRealtime is 0, so the fix and the trip start are set from inside the render. */
    private fun shoot() = paparazzi.snapshot {
        val now = SystemClock.elapsedRealtime()
        TelemetryManager.state.value = Telemetry(
            speedKmh = 68, bearing = 15f, tripStartAt = now - (42 * 60 + 18) * 1000L,
            distanceM = 24_800.0, lastFixAt = maxOf(now, 1L), lat = -6.2088, lon = 106.8456,
        )
        ScaledToDesign { HomeScreen() }
    }

    @Test
    fun navigating() = shoot()

    @Test
    fun navigatingAtNight() {
        WeatherRepo.state.value = Weather("South Jakarta, ID", 27, "NIGHT / CLEAR", isDay = false)
        Night.on = true   // HomeScreen derives it in an effect, after the first frame the snapshot takes
        shoot()
    }

    /** Long song, artist, road and place names must end in "…", never push the layout around. */
    @Test
    fun longText() {
        NavHub.state.value = NavInfo("12.5km", "SLIGHT RIGHT",
            "Jl. Letnan Jenderal S. Parman Kav. 28 (Slipi, arah Tomang dan Grogol Petamburan)", 125)
        MediaHub.info.value = MediaHub.info.value!!.copy(
            title = "Sebuah Lagu Dengan Judul Yang Sangat Panjang Sekali Hingga Tidak Muat Di Layar (Live Version)",
            artist = "Nama Penyanyi Yang Panjang feat. Kolaborator Lain • Album Kompilasi Terbaik Sepanjang Masa",
        )
        Ui.toast("YOUTUBE MUSIC NOT FOUND // PICK AN APP FROM THE LIST BELOW TO ASSIGN IT TO THIS BUTTON NOW")
        shoot()
    }

    @Test
    fun longTextAmbient() {
        MediaHub.info.value = MediaHub.info.value!!.copy(
            title = "Sebuah Lagu Dengan Judul Yang Sangat Panjang Sekali Hingga Tidak Muat Di Layar (Live Version)",
        )
        Ui.overlay = Overlay.Ambient
        shoot()
    }

    @Test
    fun cruisingLongPlace() {
        NavHub.clear()
        WeatherRepo.state.value = Weather("Kabupaten Kepulauan Seribu Utara Bagian Timur, ID", 31, "DAY / CLEAR", isDay = true)
        shoot()
    }

    @Test
    fun cruising() {
        NavHub.clear()
        shoot()
    }

    @Test
    fun allApps() {
        Ui.overlay = Overlay.Drawer
        shoot()
    }

    @Test
    fun quickSettings() {
        Ui.overlay = Overlay.QuickSettings
        shoot()
    }

    @Test
    fun ambient() {
        Ui.overlay = Overlay.Ambient
        shoot()
    }
}
