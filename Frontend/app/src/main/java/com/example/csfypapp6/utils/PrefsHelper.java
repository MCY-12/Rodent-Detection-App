package com.example.csfypapp6.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class PrefsHelper {
    private static final String PREFS_NAME = "RodentMonitorPrefs";
    public static final String KEY_MONITORED = "monitored_locations";
    public static final String KEY_DAILY = "notify_daily";
    public static final String KEY_WEEKLY = "notify_weekly";
    public static final String KEY_MONTHLY = "notify_monthly";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public PrefsHelper(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public SharedPreferences getSharedPreferences() {
        return prefs;
    }

    // Monitored locations
    public void setMonitoredLocations(Set<String> locations) {
        prefs.edit().putStringSet(KEY_MONITORED, locations).apply();
    }

    public Set<String> getMonitoredLocations() {
        return prefs.getStringSet(KEY_MONITORED, new java.util.HashSet<>());
    }

    // Notification preferences
    public void setNotificationEnabled(String type, boolean enabled) {
        prefs.edit().putBoolean(type, enabled).apply();
    }

    public boolean isNotificationEnabled(String type) {
        return prefs.getBoolean(type, false); // Default to enabled
    }
}