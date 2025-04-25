package com.example.csfypapp6.fragments;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.Manifest;
import com.example.csfypapp6.fragments.*;
import com.example.csfypapp6.R;
import com.example.csfypapp6.utils.NotificationWorker;
import com.google.android.material.navigation.NavigationView;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Setup navigation drawer
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        toggle.getDrawerArrowDrawable().setColor(getResources().getColor(R.color.white));
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // Handle navigation item selection
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_map) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new MapFragment())
                        .commit();
            } else if (id == R.id.nav_report) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new ReportFragment())
                        .commit();
            } else if (id == R.id.nav_settings) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new OptionsFragment())
                        .commit();
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // Load default fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new MapFragment())
                    .commit();
            navigationView.setCheckedItem(R.id.nav_map);
        }

        scheduleNotifications();
    }

    private void scheduleNotifications() {
        // Daily updates every 24 hours
        PeriodicWorkRequest dailyWorkRequest = new PeriodicWorkRequest.Builder(
                NotificationWorker.class, 24, TimeUnit.HOURS)
                .setInputData(new Data.Builder().putString("type", "daily").build())
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("daily_updates", ExistingPeriodicWorkPolicy.KEEP, dailyWorkRequest);

        // Weekly updates every 7 days
        PeriodicWorkRequest weeklyWorkRequest = new PeriodicWorkRequest.Builder(
                NotificationWorker.class, 7, TimeUnit.DAYS)
                .setInputData(new Data.Builder().putString("type", "weekly").build())
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("weekly_updates", ExistingPeriodicWorkPolicy.KEEP, weeklyWorkRequest);

        // Sudden alerts every 6 hours
        PeriodicWorkRequest monthlyWorkRequest = new PeriodicWorkRequest.Builder(
                NotificationWorker.class, 30, TimeUnit.DAYS)
                .setInputData(new Data.Builder().putString("type", "monthly").build())
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("monthly_updates", ExistingPeriodicWorkPolicy.KEEP, monthlyWorkRequest);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                Toast.makeText(this, "Notifications are required for this app to function properly.", Toast.LENGTH_LONG).show();
            }
        }
    }
}