package com.homeprojects.carlauncher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Bluetooth music on NWD units. Their Bluetooth runs on its own module (com.bt.bc03) and publishes no
 * MediaSession: the song arrives as broadcasts, and play / next / previous go through the module's
 * BTFeature binder, the way NWD's own music app does it. Codes from BC03BTService_AT+ 1.7.7 (K2501).
 */
object NwdBluetooth {
    private const val PKG = "com.bt.bc03"
    private const val ID3 = "com.bt.ACTION_AVRCP_MUSIC_ID3"
    private const val PLAY = "com.bt.ACTION_BT_MUSIC_PLAY"
    private const val PAUSE = "com.bt.ACTION_BT_MUSIC_PAUSE"
    private const val A2DP_RELEASE = "com.bt.ACTION_A2DP_RELEASE"

    private const val TX_PLAY_CONTROL = 23   // playControl(int): 0 play/pause, 1 previous, 2 next
    private const val TX_IS_PLAYING = 24
    private const val TX_QUERY_ID3 = 60      // makes the module re-send the ID3 broadcast

    /** The phone's song; null when nothing has played since the phone's audio connected. */
    val info = MutableStateFlow<MediaInfo?>(null)
    private var binder: IBinder? = null
    private var started = false

    val available: Boolean get() = binder != null

    fun start(context: Context) {
        if (started) return
        val app = context.applicationContext
        if (runCatching { app.packageManager.getPackageInfo(PKG, 0) }.isFailure) return
        started = true
        app.registerReceiver(receiver, IntentFilter().apply { listOf(ID3, PLAY, PAUSE, A2DP_RELEASE).forEach(::addAction) })
        runCatching { app.bindService(Intent("com.bt.ACTION_START_SERVICE").setPackage(PKG), connection, Context.BIND_AUTO_CREATE) }
    }

    fun playPause() = control(0)
    fun previous() = control(1)
    fun next() = control(2)

    private fun control(code: Int) {
        call(TX_PLAY_CONTROL, code)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service
            if (call(TX_IS_PLAYING) == 1) update(playing = true)
            call(TX_QUERY_ID3)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            binder = null
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ID3 -> update(
                    title = intent.getStringExtra("extra_avrcp_id3_title").orEmpty(),
                    artist = intent.getStringExtra("extra_avrcp_id3_artist").orEmpty(),
                )
                PLAY -> update(playing = true)
                PAUSE -> update(playing = false)
                A2DP_RELEASE -> info.value = null
            }
            MediaHub.bluetoothChanged(info.value)
        }
    }

    private fun update(title: String? = null, artist: String? = null, playing: Boolean? = null) {
        val old = info.value
        info.value = MediaInfo(
            packageName = PKG,
            title = title ?: old?.title.orEmpty(),
            artist = artist ?: old?.artist.orEmpty(),
            art = null,
            durationMs = 0,
            positionMs = 0,
            positionAt = SystemClock.elapsedRealtime(),
            speed = 0f,   // the module sends no song position; keep the clock at 00:00
            playing = playing ?: old?.playing ?: false,
        )
    }

    /** One BTFeature call; the result's int (booleans as 0/1), or null when the module isn't reachable. */
    private fun call(code: Int, arg: Int? = null): Int? {
        val b = binder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken("com.bt.BTFeature")
            if (arg != null) data.writeInt(arg)
            b.transact(code, data, reply, 0)
            reply.readException()
            if (reply.dataAvail() >= 4) reply.readInt() else 0
        } catch (e: Exception) {
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
