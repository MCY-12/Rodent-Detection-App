package com.example.csfypapp6.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Set;

import javax.xml.transform.Result;

public class NotificationWorker extends Worker {
    public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }
    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        PrefsHelper prefsHelper = new PrefsHelper(context);
        Set<String> monitoredLocations = prefsHelper.getMonitoredLocations();
        String type = getInputData().getString("type");
        if ("daily".equals(type) && prefsHelper.isNotificationEnabled(PrefsHelper.KEY_DAILY)) {
            sendDailyNotifications(context, monitoredLocations);
        } else if ("weekly".equals(type) && prefsHelper.isNotificationEnabled(PrefsHelper.KEY_WEEKLY)) {
            sendWeeklyNotifications(context, monitoredLocations);
        } else if ("monthly".equals(type) && prefsHelper.isNotificationEnabled(PrefsHelper.KEY_MONTHLY)) {
            sendMonthlyNotifications(context, monitoredLocations);
        }
        return Result.success();
    }

    private void sendDailyNotifications(Context context, Set<String> locations) {
        for (String loc : locations) {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Locations/" + loc);
            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Long dailyValue = snapshot.child("daily").getValue(Long.class);
                    if (dailyValue != null) {
                        NotificationHelper helper = new NotificationHelper(context);
                        helper.sendNotification("Daily Update", "Location " + loc + ": " + dailyValue + " rodents");
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }

    private void sendWeeklyNotifications(Context context, Set<String> locations) {
        for (String loc : locations) {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Locations/" + loc);
            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Long weeklyValue = snapshot.child("weekly").getValue(Long.class);
                    if (weeklyValue != null) {
                        NotificationHelper helper = new NotificationHelper(context);
                        helper.sendNotification("Weekly Update", "Location " + loc + ": " + weeklyValue + " rodents");
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }

    private void sendMonthlyNotifications(Context context, Set<String> locations) {
        for (String loc : locations) {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Locations/" + loc);
            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Long monthlyValue = snapshot.child("monthly").getValue(Long.class);
                    if (monthlyValue != null) {
                        NotificationHelper helper = new NotificationHelper(context);
                        helper.sendNotification("Monthly Update", "Location " + loc + ": " + monthlyValue + " rodents");
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }
}
