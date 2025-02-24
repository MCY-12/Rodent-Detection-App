package com.example.csfypapp6;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MapFragment extends Fragment implements OnMapReadyCallback {
    private GoogleMap mMap;
    private DatabaseReference locationReference;
    private static final LatLng HONG_KONG = new LatLng(22.3193, 114.1694);
    private static final float DEFAULT_ZOOM = 11f;

    private ChildEventListener childEventListener;
    private HashMap<String, Marker> markersMap = new HashMap<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
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

    private void addMarkersAndListenForChanges() {
        childEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                addOrUpdateMarker(snapshot);
            }
            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                addOrUpdateMarker(snapshot);
            }
            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String key = snapshot.getKey();
                Marker marker = markersMap.get(key);
                if (marker != null) {
                    marker.remove();
                    markersMap.remove(key);
                }
            }
            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("MapFragment", "Database error: " + error.getMessage());
            }
        };
        locationReference.addChildEventListener(childEventListener);
    }

    private int getColorForRir(double rir) {
        if (rir <= 3) return Color.GREEN;
        else if (rir <= 6) return Color.parseColor("#FFA500"); // Orange
        else return Color.RED;
    }

    private void addOrUpdateMarker(DataSnapshot snapshot) {
        String key = snapshot.getKey();
        Double latitude = snapshot.child("latitude").getValue(Double.class);
        Double longitude = snapshot.child("longitude").getValue(Double.class);
        //String locationName = snapshot.child("location_name").getValue(String.class);
        Double rir = snapshot.child("rir").getValue(Double.class);

        if (latitude == null || longitude == null || rir == null) {
            Log.w("MapFragment", "Missing data for marker: " + key);
            return;
        }

        LatLng location = new LatLng(latitude, longitude);
        Marker existingMarker = markersMap.get(key);

//        if (existingMarker == null) {
//            Marker newMarker = mMap.addMarker(new MarkerOptions()
//                    .position(location)
//                    .title(key)
//                    .snippet("RIR: " + rir));
//            markersMap.put(key, newMarker);
//        } else {
//            existingMarker.setPosition(location);
//            existingMarker.setTitle(key);
//            existingMarker.setSnippet("RIR: " + rir);
//        }

        int color = getColorForRir(rir);

        Bitmap bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(color);
        canvas.drawCircle(24, 24, 24, paint);
        BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(bitmap);

        if (existingMarker == null) {
            Marker newMarker = mMap.addMarker(new MarkerOptions()
                    .position(location)
                    .icon(icon) // <-- Set custom icon
                    .title(key));
            markersMap.put(key, newMarker);
        } else {
            existingMarker.setIcon(icon);
            existingMarker.setPosition(location);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.clear();
        markersMap.clear();

        // Map settings
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(HONG_KONG, DEFAULT_ZOOM));
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.getUiSettings().setZoomControlsEnabled(true);

        locationReference = FirebaseDatabase.getInstance().getReference("Locations");
        addMarkersAndListenForChanges();

        mMap.setOnMarkerClickListener(marker -> {
            String locationKey = marker.getTitle();
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Locations/" + locationKey);

            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    showInfoWindow(snapshot);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });

            return true; // Consume the event
        });
    }

    private void showInfoWindow(DataSnapshot snapshot) {
        View infoWindow = getView().findViewById(R.id.info_window);
        infoWindow.setVisibility(View.VISIBLE);

        // Get references to views
        TextView title = infoWindow.findViewById(R.id.title);
        TextView rirValue = infoWindow.findViewById(R.id.rir_value);
        TextView hourlyView = infoWindow.findViewById(R.id.hourly);
        TextView dailyView = infoWindow.findViewById(R.id.daily);
        TextView weeklyView = infoWindow.findViewById(R.id.weekly);

        Button toggleButton = infoWindow.findViewById(R.id.toggle_view);
        BarChart chart = infoWindow.findViewById(R.id.chart);
        LinearLayout textStats = infoWindow.findViewById(R.id.text_stats);

        // Populate data
        String locationName = snapshot.getKey();
        float rir = snapshot.child("rir").getValue(Float.class);
        Long hourly = getSafeLong(snapshot, "hourly");
        Long daily = getSafeLong(snapshot, "daily");
        Long weekly = getSafeLong(snapshot, "weekly");

        title.setText(locationName);
        rirValue.setText(String.format("RIR: %.1f", rir));

        hourlyView.setText("Last hour: " + hourly);
        dailyView.setText("Last day: " + daily);
        weeklyView.setText("Last week: " + weekly);

        // Close button
        infoWindow.findViewById(R.id.close_button).setOnClickListener(v -> {
            infoWindow.setVisibility(View.GONE);
        });

        // Toggle between chart/text
        toggleButton.setOnClickListener(v -> {
            if (chart.getVisibility() == View.VISIBLE) {
                chart.setVisibility(View.GONE);
                textStats.setVisibility(View.VISIBLE);
                toggleButton.setText("Show Chart");
            } else {
                textStats.setVisibility(View.GONE);
                chart.setVisibility(View.VISIBLE);
                toggleButton.setText("Show Text");
                setupBarChart(chart, hourly, daily, weekly);
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

    private void setupBarChart(BarChart chart, Long hourly, Long daily, Long weekly) {
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0f, hourly != null ? hourly : 0));
        entries.add(new BarEntry(1f, daily != null ? daily : 0));
        entries.add(new BarEntry(2f, weekly != null ? weekly : 0));

        BarDataSet dataSet = new BarDataSet(entries, "Rodent Activity");
        BarData barData = new BarData(dataSet);
        chart.setData(barData);
        chart.invalidate(); // Refresh
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
