package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Live weather and air quality (US AQI, PM2.5, PM10) for the chosen Indonesian city, both from Open-Meteo:
 * no key, works anywhere. Labels are Indonesian.
 */
final class WeatherAirQuality {
    private static final String TAG = "TvHome.Weather";
    private static final String PREFS_NAME = "weather_aqi_cache";
    private static final String KEY_WEATHER_JSON = "weather_json";
    private static final String KEY_AQI_JSON = "aqi_json";
    private static final String KEY_TEMP = "temp";
    private static final String KEY_CODE = "weather_code";
    private static final String KEY_AQI = "aqi";
    private static final String KEY_PM25 = "pm25";
    private static final String KEY_PM10 = "pm10";
    private static final String KEY_LAST_UPDATE = "last_update";
    private static final Locale ID = new Locale("in", "ID");
    private static final long REFRESH_INTERVAL_MS = 30L * 60 * 1000; // 30 minutes

    static class HourlyItem {
        final String time;
        final String icon;
        final int temp;
        final int rainChance;

        HourlyItem(String time, String icon, int temp, int rainChance) {
            this.time = time;
            this.icon = icon;
            this.temp = temp;
            this.rainChance = rainChance;
        }
    }

    static class DailyItem {
        final String day;
        final String icon;
        final int maxTemp;
        final int minTemp;
        final int rainChance;

        DailyItem(String day, String icon, int maxTemp, int minTemp, int rainChance) {
            this.day = day;
            this.icon = icon;
            this.maxTemp = maxTemp;
            this.minTemp = minTemp;
            this.rainChance = rainChance;
        }
    }

    static class FullWeather {
        final String location;
        final String stationId;
        final String stationPlace;
        final int temp;
        final int apparentTemp;
        final String condition;
        final String icon;
        final int highTemp;
        final int lowTemp;
        final int humidity;
        final int windSpeed;
        final int uvIndex;
        final int rainChanceToday;
        final String sunrise;
        final String sunset;
        final int aqi;
        final String aqiStatus;
        final int aqiColor;
        final String aqiParam;
        final double pm25;
        final double pm10;
        final String updatedTime;
        final List<HourlyItem> hourly;
        final List<DailyItem> daily;

        FullWeather(String location, String stationId, String stationPlace,
                    int temp, int apparentTemp, String condition, String icon,
                    int highTemp, int lowTemp, int humidity, int windSpeed, int uvIndex,
                    int rainChanceToday, String sunrise, String sunset, int aqi,
                    String aqiStatus, int aqiColor, String aqiParam, double pm25, double pm10,
                    String updatedTime, List<HourlyItem> hourly, List<DailyItem> daily) {
            this.location = location;
            this.stationId = stationId;
            this.stationPlace = stationPlace;
            this.temp = temp;
            this.apparentTemp = apparentTemp;
            this.condition = condition;
            this.icon = icon;
            this.highTemp = highTemp;
            this.lowTemp = lowTemp;
            this.humidity = humidity;
            this.windSpeed = windSpeed;
            this.uvIndex = uvIndex;
            this.rainChanceToday = rainChanceToday;
            this.sunrise = sunrise;
            this.sunset = sunset;
            this.aqi = aqi;
            this.aqiStatus = aqiStatus;
            this.aqiColor = aqiColor;
            this.aqiParam = aqiParam;
            this.pm25 = pm25;
            this.pm10 = pm10;
            this.updatedTime = updatedTime;
            this.hourly = hourly;
            this.daily = daily;
        }
    }

    private WeatherAirQuality() {
    }

    /** Forces an immediate background sync regardless of cache freshness. */
    static void sync(Context context, Runnable onUpdated) {
        syncInternal(context, onUpdated, true);
    }

    /** Fetches weather and air quality in background if due (30-min cache). */
    static void syncIfDue(Context context, Runnable onUpdated) {
        syncInternal(context, onUpdated, false);
    }

    private static void syncInternal(Context context, Runnable onUpdated, boolean force) {
        new Thread(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                long last = prefs.getLong(KEY_LAST_UPDATE, 0);
                long now = System.currentTimeMillis();
                if (!force && now - last < REFRESH_INTERVAL_MS && prefs.contains(KEY_WEATHER_JSON)) {
                    return; // Cache is still fresh
                }

                double lat = AppLocationManager.getLatitude(context);
                double lon = AppLocationManager.getLongitude(context);
                String stationId = AppLocationManager.getStationId(context);
                String defaultPlace = AppLocationManager.getStationPlace(context);

                // 1. Fetch Complete Weather Forecast (Open-Meteo)
                String weatherUrl = String.format(Locale.ROOT,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&hourly=temperature_2m,precipitation_probability,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max&timezone=auto&forecast_days=7",
                        lat, lon);
                JSONObject wJson = fetchJson(weatherUrl);

                int temp = 29;
                int code = 2; // Partly cloudy
                if (wJson != null && wJson.has("current")) {
                    JSONObject curr = wJson.getJSONObject("current");
                    temp = (int) Math.round(curr.optDouble("temperature_2m", 29.0));
                    code = curr.optInt("weather_code", 2);
                }

                // 2. Air quality (Open-Meteo, US AQI scale)
                int aqi = 50;
                double pm25 = 0, pm10 = 0;
                String aqiUrl = String.format(Locale.ROOT,
                        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=pm2_5,pm10,us_aqi",
                        lat, lon);
                JSONObject aqiJson = fetchJson(aqiUrl);
                if (aqiJson != null && aqiJson.has("current")) {
                    JSONObject curr = aqiJson.getJSONObject("current");
                    aqi = curr.optInt("us_aqi", 50);
                    pm25 = curr.optDouble("pm2_5", 0);
                    pm10 = curr.optDouble("pm10", 0);
                }

                SharedPreferences.Editor edit = prefs.edit();
                if (wJson != null) edit.putString(KEY_WEATHER_JSON, wJson.toString());
                if (aqiJson != null) edit.putString(KEY_AQI_JSON, aqiJson.toString());
                edit.putInt(KEY_TEMP, temp)
                    .putInt(KEY_CODE, code)
                    .putInt(KEY_AQI, aqi)
                    .putFloat(KEY_PM25, (float) pm25)
                    .putFloat(KEY_PM10, (float) pm10)
                    .putLong(KEY_LAST_UPDATE, now)
                    .apply();

                Log.i(TAG, "Weather & AQI synced (AQI=" + aqi + ")");

                if (onUpdated != null) {
                    new Handler(Looper.getMainLooper()).post(onUpdated);
                }
            } catch (Exception e) {
                Log.w(TAG, "Weather/AQI sync error: " + e.getMessage());
            }
        }, "weather-aqi-sync").start();
    }

    /** Formats a clean Spannable UI string for the Home screen: "⛅ 29° Cerah  •  AQI 112 Tidak Sehat" */
    static CharSequence getFormatted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int temp = prefs.getInt(KEY_TEMP, 29);
        int code = prefs.getInt(KEY_CODE, 2);
        int aqi = prefs.getInt(KEY_AQI, 50);

        String icon = getWeatherIcon(code);
        String condition = getWeatherCondition(code);

        String aqiStatus = getAqiStatus(aqi);
        int aqiColor = getAqiColor(aqi);

        SpannableStringBuilder sb = new SpannableStringBuilder();

        // Weather: e.g. "⛅ 29° Cerah"
        int startWeather = sb.length();
        sb.append(icon).append(" ").append(String.valueOf(temp)).append("° ").append(condition);
        sb.setSpan(new ForegroundColorSpan(0xFFFFFFFF), startWeather, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), startWeather, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Separator: "  •  "
        int startDot = sb.length();
        sb.append("  •  ");
        sb.setSpan(new ForegroundColorSpan(0x80FFFFFF), startDot, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Air Quality: "AQI 112 Tidak Sehat"
        int startAqi = sb.length();
        sb.append("AQI ").append(String.valueOf(aqi)).append(" ").append(aqiStatus);
        sb.setSpan(new ForegroundColorSpan(aqiColor), startAqi, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), startAqi, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return sb;
    }

    /** Returns full weather model for WeatherActivity dashboard. */
    static FullWeather getFullWeather(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String wStr = prefs.getString(KEY_WEATHER_JSON, null);
        String aStr = prefs.getString(KEY_AQI_JSON, null);

        int temp = prefs.getInt(KEY_TEMP, 29);
        int apparentTemp = temp + 3;
        int code = prefs.getInt(KEY_CODE, 2);
        int highTemp = temp + 3;
        int lowTemp = temp - 4;
        int humidity = 78;
        int windSpeed = 8;
        int uvIndex = 6;
        int rainChanceToday = 60;
        String sunrise = "06:00";
        String sunset = "18:00";

        List<HourlyItem> hourly = new ArrayList<>();
        List<DailyItem> daily = new ArrayList<>();

        if (wStr != null) {
            try {
                JSONObject wJson = new JSONObject(wStr);

                if (wJson.has("current")) {
                    JSONObject curr = wJson.getJSONObject("current");
                    temp = (int) Math.round(curr.optDouble("temperature_2m", temp));
                    apparentTemp = (int) Math.round(curr.optDouble("apparent_temperature", apparentTemp));
                    code = curr.optInt("weather_code", code);
                    humidity = (int) Math.round(curr.optDouble("relative_humidity_2m", humidity));
                    windSpeed = (int) Math.round(curr.optDouble("wind_speed_10m", windSpeed));
                }

                if (wJson.has("daily")) {
                    JSONObject d = wJson.getJSONObject("daily");
                    JSONArray maxTemps = d.optJSONArray("temperature_2m_max");
                    JSONArray minTemps = d.optJSONArray("temperature_2m_min");
                    JSONArray rains = d.optJSONArray("precipitation_probability_max");
                    JSONArray uvs = d.optJSONArray("uv_index_max");
                    JSONArray sunrises = d.optJSONArray("sunrise");
                    JSONArray sunsets = d.optJSONArray("sunset");
                    JSONArray days = d.optJSONArray("time");
                    JSONArray dailyCodes = d.optJSONArray("weather_code");

                    if (maxTemps != null && maxTemps.length() > 0) highTemp = (int) Math.round(maxTemps.optDouble(0, highTemp));
                    if (minTemps != null && minTemps.length() > 0) lowTemp = (int) Math.round(minTemps.optDouble(0, lowTemp));
                    if (rains != null && rains.length() > 0) rainChanceToday = rains.optInt(0, rainChanceToday);
                    if (uvs != null && uvs.length() > 0) uvIndex = (int) Math.round(uvs.optDouble(0, uvIndex));
                    if (sunrises != null && sunrises.length() > 0) sunrise = formatSunTime(sunrises.optString(0, sunrise));
                    if (sunsets != null && sunsets.length() > 0) sunset = formatSunTime(sunsets.optString(0, sunset));

                    // Parse 7 days
                    if (days != null) {
                        int len = Math.min(7, days.length());
                        for (int i = 0; i < len; i++) {
                            String dayStr = days.optString(i, "");
                            String dayLabel = i == 0 ? "Hari ini" : formatDayName(dayStr);
                            int c = dailyCodes != null ? dailyCodes.optInt(i, 2) : 2;
                            int mx = maxTemps != null ? (int) Math.round(maxTemps.optDouble(i, 31.0)) : 31;
                            int mn = minTemps != null ? (int) Math.round(minTemps.optDouble(i, 24.0)) : 24;
                            int r = rains != null ? rains.optInt(i, 50) : 50;
                            daily.add(new DailyItem(dayLabel, getWeatherIcon(c), mx, mn, r));
                        }
                    }
                }

                if (wJson.has("hourly")) {
                    JSONObject h = wJson.getJSONObject("hourly");
                    JSONArray times = h.optJSONArray("time");
                    JSONArray temps = h.optJSONArray("temperature_2m");
                    JSONArray rains = h.optJSONArray("precipitation_probability");
                    JSONArray codes = h.optJSONArray("weather_code");

                    if (times != null && temps != null) {
                        Calendar nowCal = Calendar.getInstance();
                        int currentHour = nowCal.get(Calendar.HOUR_OF_DAY);
                        int count = 0;
                        for (int i = 0; i < times.length() && count < 12; i++) {
                            String tStr = times.optString(i, "");
                            if (tStr.contains("T")) {
                                int hour = Integer.parseInt(tStr.substring(tStr.indexOf('T') + 1, tStr.indexOf(':')));
                                if (count > 0 || hour >= currentHour) {
                                    String label = count == 0 ? "Sekarang" : formatHourLabel(hour);
                                    int t = (int) Math.round(temps.optDouble(i, 28.0));
                                    int c = codes != null ? codes.optInt(i, 2) : 2;
                                    int r = rains != null ? rains.optInt(i, 30) : 30;
                                    hourly.add(new HourlyItem(label, getWeatherIcon(c), t, r));
                                    count++;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed parsing weather json: " + e.getMessage());
            }
        }

        // Fill fallback if empty
        if (hourly.isEmpty()) {
            hourly.add(new HourlyItem("Sekarang", "⛅", temp, 40));
        }

        if (daily.isEmpty()) {
            daily.add(new DailyItem("Hari ini", "⛅", highTemp, lowTemp, 60));
        }

        // Air Quality
        int aqi = prefs.getInt(KEY_AQI, 50);
        String aqiStatus = getAqiStatus(aqi);
        String aqiParam = "PM2.5";
        String stationPlace = AppLocationManager.getStationPlace(context);
        String stationId = AppLocationManager.getStationId(context);
        String locationName = AppLocationManager.getLocationName(context).toUpperCase(Locale.ROOT);

        double pm25 = prefs.getFloat(KEY_PM25, 0f);
        double pm10 = prefs.getFloat(KEY_PM10, 0f);
        int aqiColor = getAqiColor(aqi);

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", ID);
        String updated = sdf.format(new Date());

        return new FullWeather(locationName, stationId, stationPlace, temp, apparentTemp,
                getWeatherCondition(code), getWeatherIcon(code),
                highTemp, lowTemp, humidity, windSpeed, uvIndex, rainChanceToday,
                sunrise, sunset, aqi, aqiStatus, aqiColor, aqiParam, pm25, pm10, updated, hourly, daily);
    }

    private static String formatSunTime(String isoTime) {
        try {
            if (isoTime.contains("T")) {
                String[] parts = isoTime.substring(isoTime.indexOf('T') + 1).split(":");
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                return String.format(Locale.ROOT, "%02d:%02d", h, m);
            }
        } catch (Exception ignored) {
        }
        return "06:00";
    }

    private static String formatDayName(String ymd) {
        try {
            SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            Date d = inFmt.parse(ymd);
            if (d != null) {
                SimpleDateFormat outFmt = new SimpleDateFormat("EEE", ID);
                return outFmt.format(d);
            }
        } catch (Exception ignored) {
        }
        return ymd;
    }

    private static String formatHourLabel(int hour24) {
        return String.format(Locale.ROOT, "%02d:00", hour24);
    }

    private static String getWeatherCondition(int code) {
        switch (code) {
            case 0:
                return "Cerah";
            case 1:
            case 2:
                return "Cerah Berawan";
            case 3:
                return "Mendung";
            case 45:
            case 48:
                return "Berkabut";
            case 51:
            case 53:
            case 55:
                return "Gerimis";
            case 61:
            case 63:
            case 65:
                return "Hujan";
            case 80:
            case 81:
            case 82:
                return "Hujan Lebat";
            case 95:
            case 96:
            case 99:
                return "Badai Petir";
            default:
                return "Cerah";
        }
    }

    private static String getWeatherIcon(int code) {
        switch (code) {
            case 0:
                return "☀️";
            case 1:
            case 2:
                return "⛅";
            case 3:
                return "☁️";
            case 45:
            case 48:
                return "🌫️";
            case 51:
            case 53:
            case 55:
                return "🌦️";
            case 61:
            case 63:
            case 65:
                return "🌧️";
            case 80:
            case 81:
            case 82:
                return "⛈️";
            case 95:
            case 96:
            case 99:
                return "⚡";
            default:
                return "⛅";
        }
    }

    private static String getAqiStatus(int aqi) {
        if (aqi <= 50) return "Baik";
        if (aqi <= 100) return "Sedang";
        if (aqi <= 200) return "Tidak Sehat";
        if (aqi <= 300) return "Sangat Tidak Sehat";
        return "Berbahaya";
    }

    private static int getAqiColor(int aqi) {
        if (aqi <= 50) return 0xFF4ADE80;  // Green (Baik)
        if (aqi <= 100) return 0xFF38BDF8; // Sky Blue (Sedang)
        if (aqi <= 200) return 0xFFFB923C; // Orange (Tidak Sehat)
        if (aqi <= 300) return 0xFFEF4444; // Red (Sangat Tidak Sehat)
        return 0xFF991B1B;                 // Maroon (Berbahaya)
    }

    private static JSONObject fetchJson(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                return new JSONObject(sb.toString());
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchJson failed for " + urlStr + ": " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }
}
