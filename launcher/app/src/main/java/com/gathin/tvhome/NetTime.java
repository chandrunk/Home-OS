package com.gathin.tvhome;

import android.os.SystemClock;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * The real time, from NTP, for the launcher's own clocks. TCL firmware sets the system clock from the
 * broadcast TOT whenever the tuner is on, and Indonesian channels send it about 40 minutes off, so
 * System.currentTimeMillis() can't be trusted. The NTP answer is anchored to elapsedRealtime(), which
 * never jumps, so later changes to the system clock don't disturb it. Until the first answer arrives
 * (or offline) it falls back to the system clock.
 */
final class NetTime {
    private static final String TAG = "TvHome.NetTime";
    private static final String SERVER = "time.google.com";
    private static final long RESYNC_MS = 6L * 60 * 60 * 1000;   // drift of the TV's own clock is small
    /** NTP time minus elapsedRealtime at the last good answer; 0 = none yet. */
    private static volatile long base;
    private static volatile long syncedAt;
    private static volatile boolean syncing;

    private NetTime() {
    }

    static long now() {
        long b = base;
        return b != 0 ? b + SystemClock.elapsedRealtime() : System.currentTimeMillis();
    }

    /** Starts a background sync when there is no answer yet or the last one is old. */
    static void syncIfDue() {
        if (syncing || (base != 0 && SystemClock.elapsedRealtime() - syncedAt < RESYNC_MS)) return;
        syncing = true;
        new Thread(() -> {
            try {
                long t = query(SERVER);
                Log.i(TAG, "offset from system clock " + (t - System.currentTimeMillis()) / 1000 + " s");
            } catch (Exception e) {
                Log.w(TAG, "sync failed: " + e);
            } finally {
                syncing = false;
            }
        }, "net-time").start();
    }

    /** One SNTP request (RFC 4330). Returns the server time and stores the anchor. */
    static long query(String host) throws Exception {
        byte[] buf = new byte[48];
        buf[0] = 0x1B;   // LI 0, version 3, mode 3 (client)
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);
            InetAddress addr = InetAddress.getByName(host);
            long sent = SystemClock.elapsedRealtime();
            socket.send(new DatagramPacket(buf, buf.length, addr, 123));
            DatagramPacket reply = new DatagramPacket(buf, buf.length);
            socket.receive(reply);
            long received = SystemClock.elapsedRealtime();
            long serverTime = ntpToMillis(buf, 40);   // transmit timestamp
            // The answer was sent around the middle of the round trip.
            long mid = sent + (received - sent) / 2;
            base = serverTime - mid;
            syncedAt = received;
            return serverTime + (received - mid);
        }
    }

    /** Reads a 64-bit NTP timestamp (seconds since 1900 + fraction) as Unix milliseconds. */
    static long ntpToMillis(byte[] b, int off) {
        long seconds = 0, fraction = 0;
        for (int i = 0; i < 4; i++) seconds = (seconds << 8) | (b[off + i] & 0xFF);
        for (int i = 4; i < 8; i++) fraction = (fraction << 8) | (b[off + i] & 0xFF);
        return (seconds - 2208988800L) * 1000L + (fraction * 1000L >>> 32);
    }
}
