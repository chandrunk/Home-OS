package com.homeprojects.carlauncher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import java.util.Locale

/**
 * A button that opens one of the head unit's apps. Aftermarket units ship their own radio, Bluetooth
 * phone, DSP and DVR apps under different package names, so each shortcut tries its known packages,
 * then app names containing a keyword, and the driver can long-press to pick the right app (saved).
 * com.nwd: NWD firmware on Allwinner units (the K2501 this was built on). com.hcn / com.autochips:
 * 8227L (MediaTek, HCN/AutoChips) units, as documented by github.com/sipamungkas/headunit-flat-launcher.
 */
data class Shortcut(
    val key: String,
    val label: String,
    val icon: Int,
    val candidates: List<String>,
    val keywords: List<String> = emptyList(),
)

object Shortcuts {
    val ZLINK = Shortcut("zlink", "CarPlay / Android Auto", R.drawable.lc_smartphone,
        // Zlink, then the open-source DiPlay (CarPlay) and Headunit Revived (Android Auto) receivers.
        listOf("com.zjinnova.zlink", "com.zjinnova.zlink5", "com.shihab.diplay", "com.andrerinas.headunitrevived"),
        listOf("zlink", "carplay", "diplay"))
    val RADIO = Shortcut("radio", "Radio", R.drawable.lc_radio,
        listOf("com.nwd.radio", "com.hcn.autoradio", "com.syu.radio", "com.ts.main.radio", "com.tw.radio", "com.android.fmradio", "com.fyt.radio",
            "com.microntek.radio", "com.hct.radio"), listOf("radio"))
    val MAPS = Shortcut("maps", "Google Maps", R.drawable.lc_navigation,
        listOf(CarNotificationListener.MAPS), listOf("maps"))
    val YT_MUSIC = Shortcut("ytmusic", "YT Music", R.drawable.lc_disc_3,
        listOf(MediaHub.YT_MUSIC), listOf("yt music", "youtube music"))
    val BT_PHONE = Shortcut("btphone", "BT Phone", R.drawable.lc_phone,
        listOf("com.nwd.android.phone", "com.autochips.bluetooth", "com.syu.bt", "com.ts.bt", "com.microntek.bluetooth", "com.android.dialer"), listOf("bluetooth", "phone", "dialer"))
    val DSP = Shortcut("dsp", "DSP Equalizer", R.drawable.lc_sliders_horizontal,
        listOf("com.nwd.audioset", "com.hcn.autoeq", "com.syu.eq", "com.syu.dsp", "com.ts.eq", "com.microntek.eqaudio"), listOf("dsp", "equalizer"))
    val YOUTUBE = Shortcut("youtube", "YouTube", R.drawable.lc_tv,
        listOf("com.google.android.youtube"), listOf("youtube"))
    val SPOTIFY = Shortcut("spotify", "Spotify", R.drawable.lc_music,
        listOf("com.spotify.music"), listOf("spotify"))
    val DVR = Shortcut("dvr", "DVR Camera", R.drawable.lc_video,
        listOf("com.ankai.cardvr", "com.auto.hcamera", "com.syu.dvr", "com.ts.dvr", "com.microntek.dvr"), listOf("dvr", "dashcam", "recorder"))
    /** The first quick-app tile: the radio until the driver long-presses it and picks another app. */
    val SLOT = RADIO.copy(key = "slot1", icon = R.drawable.lc_radio_tower)
    val SETTINGS = Shortcut("settings", "Settings", R.drawable.lc_settings,
        listOf("com.android.settings"), listOf("settings"))

    /** The ALL APPS directory's first two rows, in the design's order. */
    val directory = listOf(ZLINK, RADIO, MAPS, YT_MUSIC, BT_PHONE, DSP, YOUTUBE, SPOTIFY, DVR, SETTINGS)
}

data class AppEntry(val packageName: String, val label: String, val icon: Bitmap?)

object NativeAppLauncher {
    private const val TAG = "CarHome.Launch"
    private const val PREFS = "shortcuts"

    /** Saved choice, then known packages, then a launchable app whose name or package has a keyword. */
    fun resolve(context: Context, s: Shortcut): String? {
        val pm = context.packageManager
        fun launchable(pkg: String) = pm.getLaunchIntentForPackage(pkg) != null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(s.key, null)
            ?.takeIf(::launchable)?.let { return it }
        s.candidates.firstOrNull(::launchable)?.let { return it }
        if (s.keywords.isEmpty()) return null
        return launchableApps(context, withIcons = false).firstOrNull { app ->
            val haystack = (app.label + " " + app.packageName).lowercase(Locale.ROOT)
            s.keywords.any { haystack.contains(it) }
        }?.packageName
    }

    /** The app the driver picked for this shortcut, if any (and still installed). */
    fun savedApp(context: Context, s: Shortcut): AppEntry? {
        val pkg = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(s.key, null) ?: return null
        val pm = context.packageManager
        return runCatching {
            val info = pm.getApplicationInfo(pkg, 0)
            AppEntry(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info).toBitmap())
        }.getOrNull()
    }

    fun save(context: Context, s: Shortcut, pkg: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(s.key, pkg).apply()
    }

    /** False when no app was found: the caller opens the app picker for this shortcut. */
    fun launch(context: Context, s: Shortcut): Boolean {
        val pkg = resolve(context, s) ?: return false
        return launchPackage(context, pkg)
    }

    /** Brings the app's existing task to the front (Zlink resumes its session) or starts it. */
    fun launchPackage(context: Context, pkg: String, extraFlags: Int = 0): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or extraFlags)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "can't start $pkg: $e")
            false
        }
    }

    /**
     * Opens Maps in the other half of the screen. Android honours LAUNCH_ADJACENT only where the unit's
     * firmware supports split-screen; elsewhere Maps simply opens full screen.
     */
    fun splitScreen(context: Context): Boolean {
        val pkg = resolve(context, Shortcuts.MAPS) ?: return false
        return launchPackage(context, pkg, Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    }

    /**
     * Opens the unit's Bluetooth music screen. On AutoChips units A2DP audio stays parked (no sound)
     * until BtMusicActivity is the active source, even while the phone streams.
     */
    fun openBluetoothMusic(context: Context): Boolean {
        for ((pkg, cls) in BT_MUSIC) {
            val intent = Intent().setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.packageManager.resolveActivity(intent, 0) == null) continue
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return launchPackage(context, "com.nwd.bt.music")   // NWD: its own BT music app
    }

    private val BT_MUSIC = listOf("com.autochips.bluetooth" to "com.autochips.bluetooth.BtMusicActivity")

    fun launchableApps(context: Context, withIcons: Boolean = true): List<AppEntry> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                AppEntry(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(pm).toString(),
                    icon = if (withIcons) runCatching { it.loadIcon(pm).toBitmap() }.getOrNull() else null,
                )
            }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }
}
