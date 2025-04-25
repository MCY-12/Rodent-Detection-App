package com.example.csfypapp6.fragments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import com.example.csfypapp6.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

public class ReportFragment extends Fragment implements OnMapReadyCallback {
    private GoogleMap mMap;
    private Spinner spinner;
    private EditText descriptionEditText;
    private List<Location> locations = new ArrayList<>();
    private DatabaseReference locationReference;

    private String imageDownloadUrl;
    private static final int PICK_IMAGE_REQUEST = 1;
    private Uri selectedImageUri = null;
    private ImageView imagePreview;
    private LinearLayout imagePreviewContainer;
    private Button removeImageButton;

    public class Location {
        private String name;
        private double latitude;
        private double longitude;
        private double rir;

        public Location() {}

        public Location(String name, double latitude, double longitude, double rir) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
            this.rir = rir;
        }

        public String getName() {
            return name;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public double getRir() {
            return rir;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_report, container, false);

        return view;
    }

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        spinner = view.findViewById(R.id.location_spinner);
        descriptionEditText = view.findViewById(R.id.description_edittext);

        locationReference = FirebaseDatabase.getInstance().getReference("Locations");

        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getChildFragmentManager().beginTransaction().replace(R.id.map_container, mapFragment).commit();
        mapFragment.getMapAsync(this);

        fetchLocationsFromFirebase();

        Button uploadButton = view.findViewById(R.id.upload_button);
        Button submitButton = view.findViewById(R.id.submit_button);
        uploadButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        });
        submitButton.setOnClickListener(v -> submitReport());

        imagePreview = view.findViewById(R.id.image_preview);
        imagePreviewContainer = view.findViewById(R.id.image_preview_container);
        removeImageButton = view.findViewById(R.id.remove_image_button);
        removeImageButton.setOnClickListener(v -> {
            selectedImageUri = null;
            imagePreviewContainer.setVisibility(View.GONE);
            imagePreview.setImageURI(null);
        });
    }

    private void fetchLocationsFromFirebase() {
        locationReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                locations.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String locationName = snapshot.getKey();
                    Double latitude = snapshot.child("latitude").getValue(Double.class);
                    Double longitude = snapshot.child("longitude").getValue(Double.class);
                    Double rir = snapshot.child("rir").getValue(Double.class);

                    if (latitude != null && longitude != null && rir != null) {
                        locations.add(new Location(locationName, latitude, longitude, rir));
                    }
                }
                updateSpinner();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("MapFragment", "Database error: " + databaseError.getMessage());
            }
        });
    }

    private void updateSpinner() {
        List<String> locationNames = new ArrayList<>();
        for (Location location : locations) {
            locationNames.add(location.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                locationNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Link Spinner to Map
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Location selectedLocation = locations.get(position);
                LatLng latLng = new LatLng(selectedLocation.getLatitude(), selectedLocation.getLongitude());
                if (mMap != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12f));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add markers
        for (Location location : locations) {
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            mMap.addMarker(new MarkerOptions().position(latLng).title(location.getName()));
        }

        // Set default location
        if (!locations.isEmpty()) {
            Location firstLocation = locations.get(0);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(firstLocation.getLatitude(), firstLocation.getLongitude()), 10f
            ));
        }

        // Link Map Marker Clicks to Spinner
        mMap.setOnMarkerClickListener(marker -> {
            String markerTitle = marker.getTitle();
            for (int i = 0; i < locations.size(); i++) {
                if (locations.get(i).getName().equals(markerTitle)) {
                    spinner.setSelection(i);
                    break;
                }
            }
            return false;
        });
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        String selectedItem = parent.getItemAtPosition(pos).toString();
        //reportLocation = selectedItem;
    }

//    @Override
//    public void onNothingSelected(AdapterView<?> parent) {
//        // Another interface callback
//    }

    private void uploadPicture() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            imagePreview.setImageURI(selectedImageUri);
            imagePreviewContainer.setVisibility(View.VISIBLE);
        }
    }

    private Task<Uri> uploadImageToFirebase(Uri imageUri) {
        String selectedLocation = spinner.getSelectedItem().toString();
        String fileName = "report_" + System.currentTimeMillis() + ".jpg";
        //future to do: we are renaming file as jpg even if uploaded file isn't a jpg
        //possibly need file type conversion
        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("User Reports")
                .child(selectedLocation)
                .child(fileName);

        return storageRef.putFile(imageUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return storageRef.getDownloadUrl();
                });
    }

    private Map<String, Object> createReportData() {
        long timestamp = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));

        Map<String, Object> report = new HashMap<>();
        report.put("timestamp", timestamp);
        report.put("date", sdf.format(new Date(timestamp)));
        report.put("description", descriptionEditText.getText().toString().trim());
        return report;
    }

    private void saveReportToDatabase(Map<String, Object> report, ProgressDialog progressDialog) {
        String location = spinner.getSelectedItem().toString();

        FirebaseDatabase.getInstance().getReference()
                .child("Reports")
                .child(location)
                .push()
                .setValue(report)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        resetForm();
                        Toast.makeText(requireContext(), "Report submitted!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Submission failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void submitReport() {
        if (spinner.getSelectedItem() == null) {
            Toast.makeText(requireContext(), "Select a location first", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(requireContext());
        progressDialog.setMessage("Submitting report...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        Map<String, Object> report = createReportData();

        // Chain upload tasks
        Task<Uri> uploadTask = selectedImageUri != null ?
                uploadImageToFirebase(selectedImageUri) :
                Tasks.forResult(null);

        uploadTask.addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (task.getResult() != null) {
                    report.put("imageUrl", task.getResult().toString());
                }
                saveReportToDatabase(report, progressDialog);
            } else {
                progressDialog.dismiss();
                Toast.makeText(requireContext(), "Image upload failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resetForm() {
        selectedImageUri = null;
        descriptionEditText.setText("");
        imagePreviewContainer.setVisibility(View.GONE);
        imagePreview.setImageURI(null);
    }
}