package com.homeprojects.carlauncher

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which Wi-Fi network and which phone the unit is connected to, for the status chips.
 *
 * Wi-Fi: the SSID needs the location permission (granted for the speedometer) on Android 8.1+.
 * Bluetooth: NWD units pair phones through their own Bluetooth module, not Android's (Android reports
 * Bluetooth off); its firmware keeps the phone in Settings.System bt_phone_name / bt_phone_status.
 * Elsewhere the A2DP and headset profiles say which device is connected.
 */
object Connectivity {
    /** Connected network name; null when not on Wi-Fi or the name is hidden. */
    var ssid by mutableStateOf<String?>(null)
        private set
    /** Connected phone; null when none. */
    var phone by mutableStateOf<String?>(null)
        private set
    /** Bluetooth is on (Android's adapter, or the NWD module). */
    var bluetoothOn by mutableStateOf(false)
        private set
    /** The unit pairs through the NWD module (its own phone app manages devices). */
    var nwd by mutableStateOf(false)
        private set

    /** For screenshots and previews. */
    internal fun show(ssid: String?, phone: String?) {
        this.ssid = ssid
        this.phone = phone
        bluetoothOn = true
    }

    private var profiles = mutableMapOf<Int, BluetoothProfile>()

    fun refresh(context: Context) {
        ssid = if (com.homeprojects.carlauncher.ui.Ui.wifi) readSsid(context) else null
        val r = context.contentResolver
        val nwdState = Settings.System.getString(r, "key_bt_state")
        nwd = nwdState != null
        if (nwd) {
            bluetoothOn = nwdState == "1"
            // ponytail: bt_phone_status 0 = no phone is an inference from one reading; check against
            // a connect/disconnect on the unit and adjust if the firmware counts differently.
            val status = Settings.System.getString(r, "bt_phone_status")?.toIntOrNull() ?: 0
            phone = Settings.System.getString(r, "bt_phone_name")?.takeIf { status != 0 && it.isNotBlank() }
        } else {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            bluetoothOn = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
            phone = if (bluetoothOn) connectedDevice(context, adapter) else null
        }
    }

    @Suppress("DEPRECATION")   // connectionInfo is the only SSID source before Android 12's callbacks
    private fun readSsid(context: Context): String? = runCatching {
        val raw = context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid ?: return null
        raw.trim('"').takeUnless { it.isBlank() || it == "<unknown ssid>" }
    }.getOrNull()

    /** The first device connected over A2DP or headset; profile proxies are bound once and reused. */
    private fun connectedDevice(context: Context, adapter: BluetoothAdapter?): String? {
        adapter ?: return null
        for (p in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            val proxy = profiles[p]
            if (proxy == null) {
                runCatching {
                    adapter.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) { profiles[profile] = proxy }
                        override fun onServiceDisconnected(profile: Int) { profiles.remove(profile) }
                    }, p)
                }
                continue
            }
            runCatching { proxy.connectedDevices.firstOrNull()?.name }.getOrNull()?.let { return it }
        }
        return null
    }
}
