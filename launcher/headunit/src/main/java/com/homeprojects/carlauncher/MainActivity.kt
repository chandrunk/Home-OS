package com.homeprojects.carlauncher

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.homeprojects.carlauncher.ui.HomeScreen
import com.homeprojects.carlauncher.ui.Overlay
import com.homeprojects.carlauncher.ui.Ui
import com.homeprojects.carlauncher.ui.theme.DESIGN_H
import com.homeprojects.carlauncher.ui.theme.DESIGN_W

/** The home screen. Single activity; Home brings it back to the dashboard with every overlay closed. */
class MainActivity : ComponentActivity() {

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) TelemetryManager.start(this)
    }

    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) {
            runOnUiThread {
                Ui.online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Ui.wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        }

        override fun onLost(n: Network) {
            runOnUiThread {
                Ui.online = false
                Ui.wifi = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            BackHandler { Ui.overlay = Overlay.None }   // Home never closes; Back only shuts overlays
            ScaledToDesign { HomeScreen() }
        }
        if (TelemetryManager.hasPermission(this)) TelemetryManager.start(this)
        else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(network)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Ui.overlay = Overlay.None   // Home pressed while already home
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        Ui.appsVersion++
        // The system unbinds the listener when the process dies; ask for it back.
        val listener = ComponentName(this, CarNotificationListener::class.java)
        val granted = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(listener.flattenToString()) == true
        if (granted && !CarNotificationListener.connected.value) {
            runCatching { NotificationListenerService.requestRebind(listener) }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        TelemetryManager.stop()
        runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(network) }
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

/**
 * Draws the 1280 x 720 design at 1 design px = 1 dp, scaled to fit the screen whatever its density.
 * A 1024 x 600 unit gets the same layout at 80%, letterboxed.
 */
@Composable
fun ScaledToDesign(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val scale = minOf(constraints.maxWidth / DESIGN_W.toFloat(), constraints.maxHeight / DESIGN_H.toFloat())
        CompositionLocalProvider(LocalDensity provides Density(scale, fontScale = 1f)) {
            Box(Modifier.requiredSize(DESIGN_W.dp, DESIGN_H.dp)) { content() }
        }
    }
}
