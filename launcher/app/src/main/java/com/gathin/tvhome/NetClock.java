package com.gathin.tvhome;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A TextClock that shows {@link NetTime} instead of the system clock, in Indonesian. Takes TextClock's
 * own attributes: android:format12Hour, android:format24Hour and android:timeZone.
 */
public class NetClock extends TextView {
    static final Locale ID = new Locale("in", "ID");

    private String format12, format24;
    private TimeZone zone;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            long now = NetTime.now();
            postDelayed(this, 1000 - now % 1000);
        }
    };

    public NetClock(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, new int[]{
                android.R.attr.format12Hour, android.R.attr.format24Hour, android.R.attr.timeZone});
        format12 = a.getString(0);
        format24 = a.getString(1);
        String tz = a.getString(2);
        a.recycle();
        zone = TimeZone.getTimeZone(tz != null ? tz : context.getString(R.string.time_zone));
    }

    public void setFormat12Hour(CharSequence format) {
        format12 = format != null ? format.toString() : null;
        update();
    }

    public void setFormat24Hour(CharSequence format) {
        format24 = format != null ? format.toString() : null;
        update();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NetTime.syncIfDue();
        removeCallbacks(tick);
        tick.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void update() {
        String pattern = DateFormat.is24HourFormat(getContext()) ? format24 : format12;
        if (pattern == null) pattern = format24 != null ? format24 : format12;
        if (pattern == null || pattern.isEmpty()) {
            setText("");
            return;
        }
        SimpleDateFormat f = new SimpleDateFormat(pattern, ID);
        f.setTimeZone(zone);
        String text = f.format(new Date(NetTime.now()));
        if (!text.contentEquals(getText())) setText(text);
    }
}
