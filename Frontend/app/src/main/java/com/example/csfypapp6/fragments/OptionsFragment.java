package com.example.csfypapp6.fragments;

import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.csfypapp6.utils.NotificationWorker;
import com.example.csfypapp6.utils.PrefsHelper;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.csfypapp6.utils.PrefsHelper;

import com.example.csfypapp6.R;

public class OptionsFragment extends Fragment {
    private PrefsHelper prefsHelper;
    private Set<String> monitoredLocations = new HashSet<>();
    private LinearLayout locationsContainer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_options, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefsHelper = new PrefsHelper(requireContext());

        locationsContainer = view.findViewById(R.id.locations_container);
        setupLocationSelection();
        setupNotificationSwitches(view);
    }

    private void setupLocationSelection() {
        DatabaseReference locationsRef = FirebaseDatabase.getInstance().getReference("Locations");
        locationsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> locations = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    locations.add(child.getKey());
                }
                // Update monitoredLocations to only include existing locations
                Set<String> currentMonitored = prefsHelper.getMonitoredLocations();
                Set<String> updatedMonitored = new HashSet<>(currentMonitored);
                updatedMonitored.retainAll(locations);
                prefsHelper.setMonitoredLocations(updatedMonitored);
                monitoredLocations = updatedMonitored;

                // Create checkboxes
                for (String location : locations) {
                    CheckBox cb = new CheckBox(requireContext());
                    cb.setText(location);
                    cb.setChecked(monitoredLocations.contains(location));
                    cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (isChecked && monitoredLocations.size() >= 3) {
                            buttonView.setChecked(false);
                            Toast.makeText(requireContext(), "Maximum 3 locations allowed", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (isChecked) {
                            monitoredLocations.add(location);
                        } else {
                            monitoredLocations.remove(location);
                        }
                        prefsHelper.setMonitoredLocations(monitoredLocations);
                    });
                    locationsContainer.addView(cb);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("OptionsFragment", "Failed to load locations: " + error.getMessage());
            }
        });
    }

    private void setupNotificationSwitches(View view) {
        Switch dailySwitch = view.findViewById(R.id.switch_daily);
        Switch weeklySwitch = view.findViewById(R.id.switch_weekly);
        Switch monthlySwitch = view.findViewById(R.id.switch_monthly);

        dailySwitch.setChecked(prefsHelper.isNotificationEnabled(PrefsHelper.KEY_DAILY));
        weeklySwitch.setChecked(prefsHelper.isNotificationEnabled(PrefsHelper.KEY_WEEKLY));
        monthlySwitch.setChecked(prefsHelper.isNotificationEnabled(PrefsHelper.KEY_MONTHLY));

        dailySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsHelper.setNotificationEnabled(PrefsHelper.KEY_DAILY, isChecked);
            if (isChecked) {
                Data inputData = new Data.Builder().putString("type", "daily").build();
                OneTimeWorkRequest immediateWork = new OneTimeWorkRequest.Builder(NotificationWorker.class)
                        .setInputData(inputData)
                        .build();
                WorkManager.getInstance(requireContext()).enqueue(immediateWork);
            }
        });
        weeklySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsHelper.setNotificationEnabled(PrefsHelper.KEY_WEEKLY, isChecked);
            if (isChecked) {
                Data inputData = new Data.Builder().putString("type", "weekly").build();
                OneTimeWorkRequest immediateWork = new OneTimeWorkRequest.Builder(NotificationWorker.class)
                        .setInputData(inputData)
                        .build();
                WorkManager.getInstance(requireContext()).enqueue(immediateWork);
            }
        });

        monthlySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsHelper.setNotificationEnabled(PrefsHelper.KEY_MONTHLY, isChecked);
            if (isChecked) {
                Data inputData = new Data.Builder().putString("type", "monthly").build();
                OneTimeWorkRequest immediateWork = new OneTimeWorkRequest.Builder(NotificationWorker.class)
                        .setInputData(inputData)
                        .build();
                WorkManager.getInstance(requireContext()).enqueue(immediateWork);
            }
        });
    }

}