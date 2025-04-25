package com.example.csfypapp6.fragments;

import static java.lang.Math.sqrt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.csfypapp6.R;
import com.example.csfypapp6.utils.PrefsHelper;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.renderer.LineChartRenderer;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.*;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MapFragment extends Fragment implements OnMapReadyCallback {
    private GoogleMap mMap;
    private DatabaseReference locationReference;
    private static final LatLng HONG_KONG = new LatLng(22.3193, 114.1694);
    private static final float DEFAULT_ZOOM = 11f;
    private static final long MAX_WEEKLY_VALUE = 100;

    private ChildEventListener childEventListener;
    private HashMap<String, Marker> markersMap = new HashMap<>();
    private PrefsHelper prefsHelper;
    private Set<String> monitoredLocations;
    private Map<String, DataSnapshot> latestSnapshots = new HashMap<>();

    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(PrefsHelper.KEY_MONITORED)) {
                monitoredLocations = new HashSet<>(prefsHelper.getMonitoredLocations());
                for (DataSnapshot snapshot : latestSnapshots.values()) {
                    updateMarker(snapshot);
                }
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        prefsHelper = new PrefsHelper(requireContext());
        monitoredLocations = new HashSet<>(prefsHelper.getMonitoredLocations());
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SupportMapFragment mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.map, mapFragment)
                    .commit();
        }
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        prefsHelper.getSharedPreferences().registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        prefsHelper.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.clear();
        markersMap.clear();

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(HONG_KONG, DEFAULT_ZOOM));
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.getUiSettings().setZoomControlsEnabled(true);

        locationReference = FirebaseDatabase.getInstance().getReference("Locations");
        setupDatabaseListeners();
        setupMarkerClickListener();
    }

    private void setupDatabaseListeners() {
        childEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                updateMarker(snapshot);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                updateMarker(snapshot);
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                removeMarker(snapshot);
            }

            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e("MapFragment", "Database error: " + error.getMessage());
            }
        };
        locationReference.addChildEventListener(childEventListener);
    }

    private void updateMarker(DataSnapshot snapshot) {
        String key = snapshot.getKey();
        Double latitude = snapshot.child("latitude").getValue(Double.class);
        Double longitude = snapshot.child("longitude").getValue(Double.class);
        Long weekly = snapshot.child("weekly").getValue(Long.class);
        Boolean isHighVolume = snapshot.child("user_high_volume").getValue(Boolean.class);

        if (latitude == null || longitude == null || weekly == null) {
            Log.w("MapFragment", "Missing data for marker: " + key);
            return;
        }

        LatLng location = new LatLng(latitude, longitude);
        Marker existingMarker = markersMap.get(key);
        boolean showWarning = isHighVolume != null && isHighVolume;
        boolean isMonitored = monitoredLocations.contains(key);

        BitmapDescriptor icon = showWarning ?
                createWarningMarker(isMonitored) :
                createNormalMarker(getWeeklyColor(weekly), isMonitored);

        if (existingMarker == null) {
            Marker newMarker = mMap.addMarker(new MarkerOptions()
                    .position(location)
                    .icon(icon)
                    .title(key));
            markersMap.put(key, newMarker);
        } else {
            existingMarker.setIcon(icon);
            existingMarker.setPosition(location);
        }
    }

    private int getWeeklyColor(long weekly) {
        float ratio = Math.min((float) weekly / MAX_WEEKLY_VALUE, 1f);
        return interpolateColors(Color.GREEN, Color.YELLOW, ratio);
    }

    private int interpolateColors(int startColor, int endColor, float ratio) {
        int startR = Color.red(startColor);
        int startG = Color.green(startColor);
        int startB = Color.blue(startColor);

        int endR = Color.red(endColor);
        int endG = Color.green(endColor);
        int endB = Color.blue(endColor);

        return Color.rgb(
                (int) (startR + (endR - startR) * ratio),
                (int) (startG + (endG - startG) * ratio),
                (int) (startB + (endB - startB) * ratio)
        );
    }

    private BitmapDescriptor createNormalMarker(int color, boolean isMonitored) {
        Bitmap bitmap = Bitmap.createBitmap(56, 56, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        // Black border
        paint.setColor(Color.BLACK);
        canvas.drawCircle(28, 28, isMonitored ? 28 : 24, paint);

        // Colored center
        paint.setColor(color);
        canvas.drawCircle(28, 28, 20, paint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private BitmapDescriptor createWarningMarker(boolean isMonitored) {
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        float[] tDimen = {12, 50, 52, 50, 32, 14};

        // Black border
        paint.setColor(Color.BLACK);
        if (isMonitored) {
            drawTriangle2(canvas, paint, tDimen[0]-12, tDimen[1]+7, tDimen[2]+12, tDimen[3]+7, tDimen[4], tDimen[5]-14);
        } else {
            drawTriangle2(canvas, paint, tDimen[0]-7, tDimen[1]+4, tDimen[2]+7, tDimen[3]+4, tDimen[4], tDimen[5]-8);
        }

        // Dark red fill
        paint.setColor(Color.parseColor("#BC1F20"));
        drawTriangle2(canvas, paint, tDimen[0], tDimen[1], tDimen[2], tDimen[3], tDimen[4], tDimen[5]);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private void drawTriangle2(Canvas canvas, Paint paint, float x1, float y1,
                              float x2, float y2, float x3, float y3) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void removeMarker(DataSnapshot snapshot) {
        String key = snapshot.getKey();
        Marker marker = markersMap.get(key);
        if (marker != null) {
            marker.remove();
            markersMap.remove(key);
        }
    }

    private void setupMarkerClickListener() {
        mMap.setOnMarkerClickListener(marker -> {
            String locationKey = marker.getTitle();
            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference("Locations/" + locationKey);

            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    showInfoWindow(snapshot);
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
            return true;
        });
    }

    private void showInfoWindow(DataSnapshot snapshot) {
        View infoWindow = getView().findViewById(R.id.info_window);

        infoWindow.setVisibility(View.VISIBLE);infoWindow.setAlpha(0f);
        infoWindow.setVisibility(View.VISIBLE);
        infoWindow.animate()
                .alpha(1f)
                .setDuration(200)
                .start();

        // Get references to views
        TextView title = infoWindow.findViewById(R.id.title);
        TextView rirValue = infoWindow.findViewById(R.id.rir_value);
        TextView hourly = infoWindow.findViewById(R.id.hourly);
        TextView daily = infoWindow.findViewById(R.id.daily);
        TextView weekly = infoWindow.findViewById(R.id.weekly);

        MaterialButton toggleButton = infoWindow.findViewById(R.id.toggle_view);
        LineChart chart = infoWindow.findViewById(R.id.chart);
        LinearLayout textStats = infoWindow.findViewById(R.id.text_stats);

        // Populate data
        String locationName = snapshot.getKey();
        float rir = snapshot.child("rir").getValue(Float.class);
        Long hourlyValue = getSafeLong(snapshot, "hourly");
        Long dailyValue = getSafeLong(snapshot, "daily");
        Long weeklyValue = getSafeLong(snapshot, "weekly");

        title.setText(locationName);
        rirValue.setText(String.format("RIR: %.1f", rir));

        hourly.setText(String.format(Locale.getDefault(), "Last hour: %,d", hourlyValue));
        hourly.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(requireContext(), R.drawable.calendar_clock_24px),
                null, null, null);

        daily.setText(String.format(Locale.getDefault(), "Last day: %,d", dailyValue));
        daily.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(requireContext(), R.drawable.today_24px),
                null, null, null);

        weekly.setText(String.format(Locale.getDefault(), "Last week: %,d", weeklyValue));
        weekly.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(requireContext(), R.drawable.date_range_24px),
                null, null, null);

        // Close button
        infoWindow.findViewById(R.id.close_button).setOnClickListener(v -> {
            infoWindow.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction(() -> infoWindow.setVisibility(View.GONE))
                    .start();
        });

        List<Entry> entries = new ArrayList<>();
        DataSnapshot dailySnapshot = snapshot.child("last_30_daily");
        int maxValue = 0;

        for (int i = 0; i < 30; i++) {
            DataSnapshot daySnapshot = dailySnapshot.child(String.valueOf(i));
            if (daySnapshot.exists()) {
                Long value = daySnapshot.getValue(Long.class);
                if (value != null) {
                    int intValue = value.intValue();
                    entries.add(new Entry(i, intValue));
                    if (intValue > maxValue) {
                        maxValue = intValue;
                    }
                }
            } else {
                entries.add(new Entry(i, 0));
            }
        }
        if (maxValue < 10) maxValue = 10;
        int finalMaxValue = maxValue;

        // Toggle between chart/text
        if (chart.getVisibility() == View.VISIBLE) {
            setupLineChart(chart, entries, finalMaxValue);
        } else {
            toggleButton.setText("Show Trend");
            toggleButton.setIcon(ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.monitoring_24px));
        }
        toggleButton.setOnClickListener(v -> {
            if (chart.getVisibility() == View.VISIBLE) {
                chart.setVisibility(View.GONE);
                textStats.setVisibility(View.VISIBLE);
                toggleButton.setText("Show Trend");
                toggleButton.setIcon(ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.monitoring_24px));
            } else {
                textStats.setVisibility(View.GONE);
                chart.setVisibility(View.VISIBLE);
                toggleButton.setText("Show Stats");
                toggleButton.setIcon(ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.format_list_numbered_24px));
                setupLineChart(chart, entries, finalMaxValue);
            }
        });

        TextView warningBadge = infoWindow.findViewById(R.id.warning_badge);
        DatabaseReference warningInfoRef = FirebaseDatabase.getInstance()
                .getReference("Locations/" + snapshot.getKey() + "/user_high_volume");

        warningInfoRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot statusSnapshot) {
                Boolean isHighVolume = statusSnapshot.getValue(Boolean.class);
                if (isHighVolume != null && isHighVolume) {
                    warningBadge.setVisibility(View.VISIBLE);
                } else {
                    warningBadge.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("InfoWindow", "Warning status check failed: " + error.getMessage());
            }
        });
    }

    private Long getSafeLong(DataSnapshot snapshot, String childKey) {
        if (snapshot.hasChild(childKey)) {
            try {
                return snapshot.child(childKey).getValue(Long.class);
            } catch (Exception e) {
                Log.e("DataError", "Invalid value for " + childKey, e);
            }
        }
        return 0L;
    }

    private void setupLineChart(LineChart chart, List<Entry> entries, int maxValue) {
        chart.clear();
        chart.invalidate();

        // Chart styling

        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);

        // Create dataset
        LineDataSet dataSet = new LineDataSet(entries, "Daily Activity");
        dataSet.setColor(ContextCompat.getColor(requireContext(), R.color.purple_500));
        dataSet.setCircleColor(ContextCompat.getColor(requireContext(), R.color.purple_700));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setValueTextSize(10f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        // Configure axes
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setLabelCount(9, true);
        //xAxis.setAxisMinValue(5);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return Integer.toString(30 - (int) value);
            }
        });
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM", Locale.getDefault());

            @Override
            public String getFormattedValue(float value) {
                return dateFormat.format(getDateForValue(value));
            }

//            @Override
//            public String getAxisLabel(float value, AxisBase axis) {
//                if (value == 0 || value == 29) { // Only show title on first and last labels
//                    return "Days Ago\n" + dateFormat.format(getDateForValue(value));
//                }
//                return dateFormat.format(getDateForValue(value));
//            }

            private Date getDateForValue(float value) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, -29 + (int) value);
                return cal.getTime();
            }
        });

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(maxValue * 1.2f); // Add 10% padding
        leftAxis.setGranularity(1f);
        chart.getAxisRight().setEnabled(false);

        // Set data
        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.animateY(500);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationReference != null && childEventListener != null) {
            locationReference.removeEventListener(childEventListener);
        }
        markersMap.clear();
    }
}
