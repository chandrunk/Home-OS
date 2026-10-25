package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NetTimeTest {
    @Test
    public void ntpTimestampToUnixMillis() {
        // 2026-10-25T12:00:00.500Z: Unix 1792929600 s, NTP adds 2208988800 s; fraction 0x80000000 = .5 s.
        long ntpSeconds = 1792929600L + 2208988800L;
        byte[] b = new byte[48];
        for (int i = 0; i < 4; i++) b[40 + i] = (byte) (ntpSeconds >>> (24 - 8 * i));
        b[44] = (byte) 0x80;
        assertEquals(1792929600500L, NetTime.ntpToMillis(b, 40));
    }
}
