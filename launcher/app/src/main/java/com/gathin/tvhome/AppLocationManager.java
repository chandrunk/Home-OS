package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AppLocationManager {
    private static final String TAG = "TvHome.Location";
    public static final String PREFS_NAME = "app_location_prefs";

    public static final String KEY_STATION_ID = "station_id";
    public static final String KEY_LOCATION_NAME = "location_name";
    public static final String KEY_STATION_PLACE = "station_place";
    public static final String KEY_LATITUDE = "latitude";
    public static final String KEY_LONGITUDE = "longitude";

    // Defaults to Jakarta
    public static final String DEFAULT_STATION_ID = "JKT";
    public static final String DEFAULT_LOCATION_NAME = "Jakarta";
    public static final String DEFAULT_STATION_PLACE = "Jakarta";
    public static final double DEFAULT_LATITUDE = -6.2088;
    public static final double DEFAULT_LONGITUDE = 106.8456;

    public static class Station {
        public final String id;
        public final String name;
        public final String state;
        public final String place;
        public final double lat;
        public final double lon;

        public Station(String id, String name, String state, String place, double lat, double lon) {
            this.id = id;
            this.name = name;
            this.state = state;
            this.place = place;
            this.lat = lat;
            this.lon = lon;
        }

        public String getDisplayName() {
            return name + " (" + state + ")";
        }
    }

    private static final List<Station> PRESET_STATIONS = new ArrayList<>();

    static {
        PRESET_STATIONS.add(new Station("JKT", "Jakarta", "DKI Jakarta", "Jakarta", -6.2088, 106.8456));
        PRESET_STATIONS.add(new Station("BGR", "Bogor", "Jawa Barat", "Bogor", -6.5971, 106.8060));
        PRESET_STATIONS.add(new Station("DPK", "Depok", "Jawa Barat", "Depok", -6.4025, 106.7942));
        PRESET_STATIONS.add(new Station("TNG", "Tangerang", "Banten", "Tangerang", -6.1783, 106.6319));
        PRESET_STATIONS.add(new Station("TGS", "Tangerang Selatan", "Banten", "Tangerang Selatan", -6.2886, 106.7179));
        PRESET_STATIONS.add(new Station("BKS", "Bekasi", "Jawa Barat", "Bekasi", -6.2383, 106.9756));
        PRESET_STATIONS.add(new Station("BDG", "Bandung", "Jawa Barat", "Bandung", -6.9175, 107.6191));
        PRESET_STATIONS.add(new Station("CBN", "Cirebon", "Jawa Barat", "Cirebon", -6.7320, 108.5523));
        PRESET_STATIONS.add(new Station("SRG", "Semarang", "Jawa Tengah", "Semarang", -6.9667, 110.4167));
        PRESET_STATIONS.add(new Station("SOC", "Solo", "Jawa Tengah", "Solo", -7.5755, 110.8243));
        PRESET_STATIONS.add(new Station("YOG", "Yogyakarta", "DI Yogyakarta", "Yogyakarta", -7.7956, 110.3695));
        PRESET_STATIONS.add(new Station("SUB", "Surabaya", "Jawa Timur", "Surabaya", -7.2575, 112.7521));
        PRESET_STATIONS.add(new Station("MLG", "Malang", "Jawa Timur", "Malang", -7.9666, 112.6326));
        PRESET_STATIONS.add(new Station("DPS", "Denpasar", "Bali", "Denpasar", -8.6705, 115.2126));
        PRESET_STATIONS.add(new Station("MTR", "Mataram", "NTB", "Mataram", -8.5833, 116.1167));
        PRESET_STATIONS.add(new Station("KOE", "Kupang", "NTT", "Kupang", -10.1772, 123.6070));
        PRESET_STATIONS.add(new Station("BTJ", "Banda Aceh", "Aceh", "Banda Aceh", 5.5483, 95.3238));
        PRESET_STATIONS.add(new Station("MDN", "Medan", "Sumatera Utara", "Medan", 3.5952, 98.6722));
        PRESET_STATIONS.add(new Station("PDG", "Padang", "Sumatera Barat", "Padang", -0.9471, 100.4172));
        PRESET_STATIONS.add(new Station("PKU", "Pekanbaru", "Riau", "Pekanbaru", 0.5071, 101.4478));
        PRESET_STATIONS.add(new Station("BTH", "Batam", "Kepulauan Riau", "Batam", 1.0456, 104.0305));
        PRESET_STATIONS.add(new Station("PLM", "Palembang", "Sumatera Selatan", "Palembang", -2.9761, 104.7754));
        PRESET_STATIONS.add(new Station("TKG", "Bandar Lampung", "Lampung", "Bandar Lampung", -5.3971, 105.2668));
        PRESET_STATIONS.add(new Station("PNK", "Pontianak", "Kalimantan Barat", "Pontianak", -0.0263, 109.3425));
        PRESET_STATIONS.add(new Station("BDJ", "Banjarmasin", "Kalimantan Selatan", "Banjarmasin", -3.3194, 114.5908));
        PRESET_STATIONS.add(new Station("BPN", "Balikpapan", "Kalimantan Timur", "Balikpapan", -1.2379, 116.8529));
        PRESET_STATIONS.add(new Station("SRI", "Samarinda", "Kalimantan Timur", "Samarinda", -0.5022, 117.1536));
        PRESET_STATIONS.add(new Station("UPG", "Makassar", "Sulawesi Selatan", "Makassar", -5.1477, 119.4327));
        PRESET_STATIONS.add(new Station("MDC", "Manado", "Sulawesi Utara", "Manado", 1.4748, 124.8421));
        PRESET_STATIONS.add(new Station("AMQ", "Ambon", "Maluku", "Ambon", -3.6954, 128.1814));
        PRESET_STATIONS.add(new Station("DJJ", "Jayapura", "Papua", "Jayapura", -2.5337, 140.7181));
    }

    public static List<Station> getPresetStations() {
        return Collections.unmodifiableList(PRESET_STATIONS);
    }

    public static String getStationId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_STATION_ID, DEFAULT_STATION_ID);
    }

    public static String getLocationName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LOCATION_NAME, DEFAULT_LOCATION_NAME);
    }

    public static String getStationPlace(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_STATION_PLACE, DEFAULT_STATION_PLACE);
    }

    public static double getLatitude(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        try {
            return Double.parseDouble(prefs.getString(KEY_LATITUDE, String.valueOf(DEFAULT_LATITUDE)));
        } catch (Exception ignored) {
            return DEFAULT_LATITUDE;
        }
    }

    public static double getLongitude(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        try {
            return Double.parseDouble(prefs.getString(KEY_LONGITUDE, String.valueOf(DEFAULT_LONGITUDE)));
        } catch (Exception ignored) {
            return DEFAULT_LONGITUDE;
        }
    }

    public static void setStation(Context context, Station s) {
        if (s == null) return;
        setCustomLocation(context, s.id, s.name, s.place, s.lat, s.lon);
    }

    public static void setCustomLocation(Context context, String stationId, String locationName, String place, double lat, double lon) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_STATION_ID, stationId)
                .putString(KEY_LOCATION_NAME, locationName)
                .putString(KEY_STATION_PLACE, place)
                .putString(KEY_LATITUDE, String.valueOf(lat))
                .putString(KEY_LONGITUDE, String.valueOf(lon))
                .apply();

        Log.i(TAG, "Location set to: " + locationName + " (" + stationId + ") [" + lat + ", " + lon + "]");

        // Trigger immediate sync for Weather
        WeatherAirQuality.sync(context, null);
    }

    public static Station findStationByQuery(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (Station s : PRESET_STATIONS) {
            if (s.id.equalsIgnoreCase(q) || s.name.toLowerCase(Locale.ROOT).contains(q) || s.state.toLowerCase(Locale.ROOT).contains(q)) {
                return s;
            }
        }
        return null;
    }

    public static Station findNearestStation(double lat, double lon) {
        Station best = null;
        double minDistance = Double.MAX_VALUE;
        for (Station s : PRESET_STATIONS) {
            double dLat = s.lat - lat;
            double dLon = s.lon - lon;
            double dist = dLat * dLat + dLon * dLon;
            if (dist < minDistance) {
                minDistance = dist;
                best = s;
            }
        }
        return best != null ? best : PRESET_STATIONS.get(0);
    }
}
