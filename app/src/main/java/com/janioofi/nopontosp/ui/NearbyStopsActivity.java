package com.janioofi.nopontosp.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.janioofi.nopontosp.R;
import com.janioofi.nopontosp.databinding.ActivityNearbyStopsBinding;
import com.janioofi.nopontosp.domain.Parada;
import com.janioofi.nopontosp.ui.adapters.NearbyStopAdapter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class NearbyStopsActivity extends AppCompatActivity {
    private static final int REQUEST_LOCATION = 301;
    private static final int RESULT_LIMIT = 25;

    private ActivityNearbyStopsBinding binding;
    private LocationManager locationManager;
    private NearbyStopAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean loading;
    private LocationListener pendingListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNearbyStopsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        adapter = new NearbyStopAdapter(this::openStop);
        binding.recyclerNearbyStops.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerNearbyStops.setAdapter(adapter);
        binding.btnRefreshLocation.setOnClickListener(v -> findLocation());
        findLocation();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (pendingListener != null) locationManager.removeUpdates(pendingListener);
        super.onDestroy();
    }

    private void findLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQUEST_LOCATION);
            binding.textNearbyMessage.setText(R.string.nearby_request_location);
            return;
        }

        binding.progressNearby.setVisibility(View.VISIBLE);
        binding.textNearbyMessage.setText(R.string.nearby_loading);
        Location cached = lastKnownLocation();
        if (cached != null) {
            showNearby(cached);
            return;
        }
        requestSingleLocation();
    }

    private Location lastKnownLocation() {
        Location best = null;
        try {
            for (String provider : locationManager.getProviders(true)) {
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (candidate != null && (best == null
                        || candidate.getTime() > best.getTime())) best = candidate;
            }
        } catch (SecurityException error) {
            binding.progressNearby.setVisibility(View.GONE);
            binding.textNearbyMessage.setText(R.string.nearby_request_location);
        }
        return best;
    }

    private void requestSingleLocation() {
        List<String> providers = locationManager.getProviders(true);
        if (providers.isEmpty()) {
            showLocationUnavailable();
            return;
        }
        pendingListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                locationManager.removeUpdates(this);
                pendingListener = null;
                handler.removeCallbacks(timeout);
                showNearby(location);
            }

            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };
        try {
            for (String provider : providers) {
                locationManager.requestLocationUpdates(provider, 0L, 0f, pendingListener, Looper.getMainLooper());
            }
            handler.postDelayed(timeout, 12_000L);
        } catch (SecurityException error) {
            pendingListener = null;
            showLocationUnavailable();
        }
    }

    private final Runnable timeout = this::showLocationUnavailable;

    private void showLocationUnavailable() {
        if (pendingListener != null) {
            locationManager.removeUpdates(pendingListener);
            pendingListener = null;
        }
        binding.progressNearby.setVisibility(View.GONE);
        binding.textNearbyMessage.setText(R.string.nearby_location_unavailable);
    }

    private void showNearby(Location location) {
        if (loading) return;
        loading = true;
        binding.progressNearby.setVisibility(View.VISIBLE);
        binding.textNearbyMessage.setText(R.string.nearby_loading);
        new Thread(() -> {
            try {
                List<NearbyStopAdapter.Item> closest = readClosestStops(location);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    loading = false;
                    binding.progressNearby.setVisibility(View.GONE);
                    adapter.submit(closest);
                    binding.textNearbyMessage.setText(closest.isEmpty()
                            ? R.string.nearby_empty : R.string.nearby_result_count);
                });
            } catch (Exception error) {
                android.util.Log.e("NearbyStopsActivity", "Não foi possível ler os pontos GTFS.", error);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    loading = false;
                    binding.progressNearby.setVisibility(View.GONE);
                    binding.textNearbyMessage.setText(R.string.nearby_data_error);
                });
            }
        }, "nearby-stops").start();
    }

    private List<NearbyStopAdapter.Item> readClosestStops(Location origin) throws Exception {
        List<NearbyStopAdapter.Item> closest = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open("gtfs/stops.txt"), StandardCharsets.UTF_8))) {
            String line;
            reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> fields = parseCsv(line);
                if (fields.size() < 5) continue;
                int code;
                double lat, lon;
                try {
                    code = Integer.parseInt(fields.get(0));
                    lat = Double.parseDouble(fields.get(3));
                    lon = Double.parseDouble(fields.get(4));
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (lat < -90 || lat > 90 || lon < -180 || lon > 180) continue;
                float[] distance = new float[1];
                Location.distanceBetween(origin.getLatitude(), origin.getLongitude(), lat, lon, distance);
                closest.add(new NearbyStopAdapter.Item(
                        new Parada(code, fields.get(1), fields.get(2), lat, lon), distance[0]));
            }
        }
        Collections.sort(closest, Comparator.comparingDouble(item -> item.distanceMeters));
        return new ArrayList<>(closest.subList(0, Math.min(RESULT_LIMIT, closest.size())));
    }

    private static List<String> parseCsv(String row) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < row.length(); i++) {
            char ch = row.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < row.length() && row.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(ch);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    private void openStop(Parada stop) {
        Intent intent = new Intent(this, MapaActivity.class);
        intent.putExtra(MapaActivity.EXTRA_CODIGO_PARADA, stop.codigoParada);
        intent.putExtra(MapaActivity.EXTRA_NOME_PARADA, stop.nome);
        intent.putExtra(MapaActivity.EXTRA_PARADA_LAT, stop.latitude);
        intent.putExtra(MapaActivity.EXTRA_PARADA_LNG, stop.longitude);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                findLocation();
            } else {
                showLocationUnavailable();
            }
        }
    }
}
