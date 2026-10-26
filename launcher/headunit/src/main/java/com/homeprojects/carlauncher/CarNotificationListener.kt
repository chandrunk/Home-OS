package com.homeprojects.carlauncher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Two jobs, both needing notification access: it is the key to MediaSessionManager (YT Music,
 * Bluetooth audio), and it reads Google Maps' turn-by-turn notification.
 */
class CarNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        connected.value = true
        MediaHub.attach(this)
        runCatching { activeNotifications }.getOrNull()?.forEach(::onPosted)
    }

    override fun onListenerDisconnected() {
        connected.value = false
        MediaHub.detach()
        NavHub.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = onPosted(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == NavHub.key) NavHub.clear()
    }

    private fun onPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPS || !sbn.isOngoing) return
        val n = sbn.notification
        val extras = n.extras
        val info = parseMapsNotification(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        ) ?: return
        NavHub.key = sbn.key
        NavHub.arrow.value = runCatching { n.getLargeIcon()?.loadDrawable(this)?.toBitmap() }.getOrNull()
        NavHub.state.value = info
    }

    companion object {
        const val MAPS = "com.google.android.apps.maps"
        val connected = MutableStateFlow(false)
    }
}

/** The current Google Maps step; null when not navigating. */
object NavHub {
    val state = MutableStateFlow<NavInfo?>(null)
    /** Maps' own turn arrow, drawn white on the black direction box. */
    val arrow = MutableStateFlow<Bitmap?>(null)
    var key: String? = null

    fun clear() {
        key = null
        state.value = null
        arrow.value = null
    }
}

enum class MediaSource { YT, BT }

data class MediaInfo(
    val packageName: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val durationMs: Long,
    val positionMs: Long,
    /** elapsedRealtime of positionMs, so the bar can advance between updates. */
    val positionAt: Long,
    val speed: Float,
    val playing: Boolean,
) {
    fun positionNow(now: Long = SystemClock.elapsedRealtime()): Long {
        val p = if (playing) positionMs + ((now - positionAt) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/**
 * Follows the active media sessions and controls the one that matches the selected tab: the
 * Bluetooth tab takes the head unit's Bluetooth audio session, the YT Music tab takes YT Music or any
 * other player (Spotify too). A playing session wins over a paused one.
 */
object MediaHub {
    const val YT_MUSIC = "com.google.android.apps.youtube.music"
    private val BT_PACKAGES = setOf("com.android.bluetooth", "com.nwd.bt.music", "com.autochips.bluetooth", "com.syu.bt", "com.ts.bt", "com.microntek.bluetooth")

    val source = MutableStateFlow(MediaSource.YT)
    val info = MutableStateFlow<MediaInfo?>(null)

    private val main = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    private var current: MediaController? = null

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        controllers = list.orEmpty()
        pick()
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = pick()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = pick()
    }

    fun attach(context: Context) {
        val m = context.getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(context, CarNotificationListener::class.java)
        try {
            controllers = m.getActiveSessions(component)
            m.addOnActiveSessionsChangedListener(sessionsChanged, component, main)
        } catch (e: SecurityException) {
            return   // notification access was taken away
        }
        manager = m
        pick()
    }

    fun detach() {
        manager?.removeOnActiveSessionsChangedListener(sessionsChanged)
        manager = null
        current?.unregisterCallback(callback)
        current = null
        controllers = emptyList()
        info.value = null
    }

    fun select(s: MediaSource) {
        source.value = s
        pick()
    }

    /** NWD units: no Bluetooth session, the module's own controls stand in for it. */
    private val nwd: Boolean get() = current == null && source.value == MediaSource.BT && NwdBluetooth.available

    val hasSession: Boolean get() = current != null || nwd

    fun playPause() {
        if (nwd) return NwdBluetooth.playPause()
        val c = current ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() {
        if (nwd) return NwdBluetooth.next()
        current?.transportControls?.skipToNext()
    }

    fun previous() {
        if (nwd) return NwdBluetooth.previous()
        current?.transportControls?.skipToPrevious()
    }

    /** The phone started playing over NWD Bluetooth: show it, unless another player is playing. */
    fun bluetoothChanged(bt: MediaInfo?) {
        if (bt?.playing == true && current?.playbackState?.state != PlaybackState.STATE_PLAYING) source.value = MediaSource.BT
        pick()
    }

    private fun isBluetooth(pkg: String) = pkg in BT_PACKAGES || pkg.contains("bluetooth", ignoreCase = true)

    private fun pick() {
        val bt = source.value == MediaSource.BT
        val candidates = controllers.filter { isBluetooth(it.packageName) == bt }
        val next = candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: candidates.firstOrNull { it.packageName == YT_MUSIC }
            ?: candidates.firstOrNull()
        if (next?.sessionToken != current?.sessionToken) {
            current?.unregisterCallback(callback)
            current = next
            next?.registerCallback(callback, main)
        }
        publish()
    }

    private fun publish() {
        val c = current
        if (c == null) {
            info.value = if (source.value == MediaSource.BT) NwdBluetooth.info.value else null
            return
        }
        val md = c.metadata
        val ps = c.playbackState
        info.value = MediaInfo(
            packageName = c.packageName,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
            artist = listOfNotNull(
                md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                md?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            ).filter { it.isNotBlank() }.joinToString(" • "),
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            positionMs = ps?.position ?: 0L,
            positionAt = ps?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            playing = ps?.state == PlaybackState.STATE_PLAYING,
        )
    }
}

fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val w = intrinsicWidth.takeIf { it > 0 } ?: 96
    val h = intrinsicHeight.takeIf { it > 0 } ?: 96
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(b)
    setBounds(0, 0, w, h)
    draw(canvas)
    return b
}
