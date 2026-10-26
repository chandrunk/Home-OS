package com.homeprojects.carlauncher

import android.content.Context
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The car's headlights, as far as an app can tell. Aftermarket units wire the headlight (ILL) signal
 * to the MCU, which either lowers Android's screen brightness setting or switches the system to
 * night uiMode. Many dim the backlight in hardware instead, where Android sees neither: then only
 * sunset (Theme.kt) turns night on. Reading the brightness setting needs no permission.
 */
object CarHeadlightDetector {
    /** Settings.System.SCREEN_BRIGHTNESS, 0..255; -1 until read or when the unit doesn't expose it. */
    var brightness by mutableIntStateOf(-1)
        private set
    /** Configuration.uiMode says night. */
    var uiNight by mutableStateOf(false)
        private set

    private var observer: ContentObserver? = null

    fun start(context: Context) {
        if (observer != null) return
        val resolver = context.contentResolver
        fun read() {
            brightness = runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(-1)
        }
        read()
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = read()
        }.also {
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, it)
        }
        onConfiguration(context.resources.configuration)
    }

    fun stop(context: Context) {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    fun onConfiguration(config: Configuration) {
        uiNight = config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    /** Headlights on: night uiMode, or the backlight setting under the driver's threshold. */
    fun headlightsOn(threshold: Int): Boolean = uiNight || brightness in 0 until threshold
}
