package com.janioofi.nopontosp.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.janioofi.nopontosp.BuildConfig;
import com.janioofi.nopontosp.R;
import com.janioofi.nopontosp.databinding.ActivityStopScheduleBinding;
import com.janioofi.nopontosp.repository.GtfsTransitStore;
import com.janioofi.nopontosp.ui.adapters.GtfsDepartureAdapter;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StopScheduleActivity extends AppCompatActivity {

    private static final int ARRIVALS_PAGE_SIZE = 15;
    private static final int REQUEST_LOCATION = 307;
    private static final long LOCATION_TIMEOUT_MS = 12_000L;
    private ActivityStopScheduleBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler locationHandler = new Handler(Looper.getMainLooper());
    private GtfsTransitStore store;
    private StopDotOverlay stopOverlay;
    private MyLocationNewOverlay myLocationOverlay;
    private LocationManager locationManager;
    private LocationListener pendingLocationListener;
    private BottomSheetBehavior<View> sheetBehavior;
    private PageController<GtfsTransitStore.Departure> pager;
    private final List<GtfsTransitStore.Route> routeOptions = new ArrayList<>();
    private String selectedStopId;
    private String selectedRouteId;
    private int queryGeneration;
    private boolean cameraCenteredOnUser;
    private Location latestLocation;
    private final Runnable locationTimeout = this::stopLocationUpdates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(
                "NoPontoSP/" + BuildConfig.VERSION_NAME + " (Android; package=" + getPackageName() + ")");
        binding = ActivityStopScheduleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK);
        binding.mapView.setMultiTouchControls(true);
        binding.mapView.getController().setZoom(14.0);
        binding.mapView.getController().setCenter(new GeoPoint(-23.5505, -46.6333));
        binding.textMapAttribution.setText(R.string.map_attribution_light);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        myLocationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(this), binding.mapView);
        Bitmap locationMarker = locationBitmap();
        myLocationOverlay.setPersonIcon(locationMarker);
        myLocationOverlay.setDirectionIcon(locationMarker);
        myLocationOverlay.setPersonAnchor(0.5f, 0.5f);
        myLocationOverlay.setDirectionAnchor(0.5f, 0.5f);
        binding.mapView.getOverlays().add(myLocationOverlay);
        binding.btnMyLocation.setOnClickListener(v -> centerOnUserLocation());
        sheetBehavior = BottomSheetBehavior.from(binding.schedulePanel);
        sheetBehavior.setFitToContents(false);
        sheetBehavior.setHalfExpandedRatio(0.42f);
        sheetBehavior.setHideable(false);
        sheetBehavior.setDraggable(true);
        sheetBehavior.setPeekHeight(dp(152));
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        GtfsDepartureAdapter adapter = new GtfsDepartureAdapter(true);
        binding.recyclerStopSchedules.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerStopSchedules.setAdapter(adapter);
        pager = new PageController<>(binding.recyclerStopSchedules, adapter::submitList,
                ARRIVALS_PAGE_SIZE);
        binding.spinnerStopRoutes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                selectedRouteId = position <= 0 ? null : routeOptions.get(position - 1).id;
                loadArrivals();
            }

            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        ensureLocationPermission();

        executor.execute(() -> {
            try {
                GtfsTransitStore loaded = GtfsTransitStore.load(getAssets());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    store = loaded;
                    binding.progressGtfs.setVisibility(View.GONE);
                    stopOverlay = new StopDotOverlay(loaded.getStops());
                    binding.mapView.getOverlays().add(stopOverlay);
                    binding.mapView.invalidate();
                    binding.textArrivalStatus.setText(R.string.gtfs_zoom_map_hint);
                });
            } catch (Exception error) {
                android.util.Log.e("StopScheduleActivity", "Não foi possível carregar o GTFS.", error);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    binding.progressGtfs.setVisibility(View.GONE);
                    binding.textArrivalStatus.setText(R.string.gtfs_data_error);
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.mapView.onResume();
        if (hasLocationPermission()) {
            enableLocationOverlay();
            if (!cameraCenteredOnUser) requestUserLocation();
        }
    }

    @Override
    protected void onPause() {
        stopLocationUpdates();
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation();
        binding.mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        queryGeneration++;
        stopLocationUpdates();
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation();
        locationHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void selectStop(GtfsTransitStore.Stop stop) {
        selectedStopId = stop.id;
        selectedRouteId = null;
        binding.textStopName.setText(stop.name);
        binding.textArrivalStatus.setText(R.string.msg_carregando);
        binding.spinnerStopRoutes.setVisibility(View.VISIBLE);
        binding.recyclerStopSchedules.setVisibility(View.VISIBLE);
        List<GtfsTransitStore.Route> routes = store.getRoutesForStop(stop.id);
        routeOptions.clear();
        routeOptions.addAll(routes);
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.gtfs_all_routes));
        for (GtfsTransitStore.Route route : routes) labels.add(route.toString());
        ArrayAdapter<String> routeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        routeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerStopRoutes.setAdapter(routeAdapter);
        binding.spinnerStopRoutes.setSelection(0);
        if (stopOverlay != null) stopOverlay.setSelected(stop);
        binding.mapView.getController().animateTo(new GeoPoint(stop.latitude, stop.longitude));
        sheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
        loadArrivals();
    }

    private void loadArrivals() {
        if (store == null || selectedStopId == null) return;
        int generation = ++queryGeneration;
        String stopId = selectedStopId;
        String routeId = selectedRouteId;
        Calendar now = Calendar.getInstance();
        GtfsTransitStore.DayType dayType =
                GtfsTransitStore.dayTypeForCalendarDay(now.get(Calendar.DAY_OF_WEEK));
        int nowSeconds = now.get(Calendar.HOUR_OF_DAY) * 3600
                + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND);
        binding.textArrivalStatus.setText(R.string.msg_carregando);
        executor.execute(() -> {
            List<GtfsTransitStore.Departure> arrivals =
                    store.getUpcomingArrivals(stopId, routeId, dayType, nowSeconds);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != queryGeneration) return;
                pager.setItems(arrivals);
                if (arrivals.isEmpty()) {
                    binding.textArrivalStatus.setText(R.string.gtfs_no_upcoming_arrivals);
                } else {
                    binding.textArrivalStatus.setText(getString(
                            R.string.gtfs_arrivals_count, arrivals.size()));
                }
            });
        });
    }

    private void ensureLocationPermission() {
        if (hasLocationPermission()) {
            enableLocationOverlay();
            requestUserLocation();
            return;
        }
        ActivityCompat.requestPermissions(this, new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, REQUEST_LOCATION);
        binding.textArrivalStatus.setText(R.string.nearby_request_location);
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void enableLocationOverlay() {
        if (myLocationOverlay != null && !myLocationOverlay.isMyLocationEnabled()) {
            myLocationOverlay.enableMyLocation();
        }
    }

    private void centerOnUserLocation() {
        if (!hasLocationPermission()) {
            ensureLocationPermission();
            return;
        }
        if (latestLocation != null) {
            animateToUser(latestLocation);
        } else {
            requestUserLocation();
        }
    }

    private void requestUserLocation() {
        if (!hasLocationPermission()) return;
        enableLocationOverlay();
        Location cached = lastKnownLocation();
        if (cached != null) {
            latestLocation = cached;
            if (!cameraCenteredOnUser) animateToUser(cached);
            return;
        }
        if (pendingLocationListener != null) return;
        List<String> providers = locationManager.getProviders(true);
        if (providers.isEmpty()) {
            showLocationUnavailable();
            return;
        }
        pendingLocationListener = location -> {
            latestLocation = location;
            stopLocationUpdates();
            if (!cameraCenteredOnUser) animateToUser(location);
        };
        try {
            for (String provider : providers) {
                locationManager.requestLocationUpdates(provider, 0L, 0f, pendingLocationListener,
                        Looper.getMainLooper());
            }
            locationHandler.postDelayed(locationTimeout, LOCATION_TIMEOUT_MS);
        } catch (SecurityException error) {
            android.util.Log.w("StopScheduleActivity", "Permissão de localização não disponível.", error);
            stopLocationUpdates();
            showLocationUnavailable();
        }
    }

    private Location lastKnownLocation() {
        Location best = null;
        try {
            for (String provider : locationManager.getProviders(true)) {
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) {
                    best = candidate;
                }
            }
        } catch (SecurityException error) {
            android.util.Log.w("StopScheduleActivity", "Não foi possível consultar a última localização.", error);
        }
        return best;
    }

    private void animateToUser(Location location) {
        cameraCenteredOnUser = true;
        binding.mapView.getController().setZoom(15.5);
        binding.mapView.getController().animateTo(
                new GeoPoint(location.getLatitude(), location.getLongitude()));
        if (selectedStopId == null) binding.textArrivalStatus.setText(R.string.gtfs_zoom_map_hint);
    }

    private void showLocationUnavailable() {
        if (selectedStopId == null) {
            binding.textArrivalStatus.setText(R.string.nearby_location_unavailable);
        }
    }

    private void stopLocationUpdates() {
        locationHandler.removeCallbacks(locationTimeout);
        if (pendingLocationListener != null) {
            try {
                locationManager.removeUpdates(pendingLocationListener);
            } catch (SecurityException error) {
                android.util.Log.w("StopScheduleActivity", "Não foi possível encerrar a busca de localização.", error);
            }
            pendingLocationListener = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCATION) return;
        if (hasLocationPermission()) {
            enableLocationOverlay();
            requestUserLocation();
        } else {
            binding.textArrivalStatus.setText(R.string.nearby_request_location);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Bitmap locationBitmap() {
        int size = dp(40);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float center = size / 2f;
        paint.setColor(ContextCompat.getColor(this, R.color.location_halo));
        canvas.drawCircle(center, center, size * 0.49f, paint);
        paint.setColor(ContextCompat.getColor(this, R.color.surface_white));
        canvas.drawCircle(center, center, size * 0.32f, paint);
        paint.setColor(ContextCompat.getColor(this, R.color.marker_veiculo));
        canvas.drawCircle(center, center, size * 0.23f, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(center, center, size * 0.09f, paint);
        return bitmap;
    }

    private final class StopDotOverlay extends Overlay {
        private final List<GtfsTransitStore.Stop> stops;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Point screenPoint = new Point();
        private final Drawable stopIcon = ContextCompat.getDrawable(
                StopScheduleActivity.this, R.drawable.ic_bus_stop);
        private GtfsTransitStore.Stop selected;
        private final int markerColor = androidx.core.content.ContextCompat.getColor(
                StopScheduleActivity.this, R.color.primary_teal);

        StopDotOverlay(List<GtfsTransitStore.Stop> stops) {
            this.stops = stops;
        }

        void setSelected(GtfsTransitStore.Stop stop) {
            selected = stop;
            invalidateMap();
        }

        @Override
        public void draw(Canvas canvas, MapView mapView, boolean shadow) {
            if (shadow || mapView.getZoomLevelDouble() < 12.0) return;
            double north = mapView.getBoundingBox().getLatNorth();
            double south = mapView.getBoundingBox().getLatSouth();
            double east = mapView.getBoundingBox().getLonEast();
            double west = mapView.getBoundingBox().getLonWest();
            float density = getResources().getDisplayMetrics().density;
            double zoom = mapView.getZoomLevelDouble();
            float zoomProgress = (float) Math.max(0d, Math.min(1d, (zoom - 12d) / 7d));
            float dotRadius = (2f + 2.5f * zoomProgress) * density;
            float iconSize = (8f + 10f * zoomProgress) * density;
            float markerRadius = iconSize * 0.62f;
            float gridSize = (16f + 8f * zoomProgress) * density;
            Set<Long> occupiedCells = new HashSet<>();
            for (GtfsTransitStore.Stop stop : stops) {
                if (stop.latitude > north || stop.latitude < south
                        || stop.longitude > east || stop.longitude < west) continue;
                mapView.getProjection().toPixels(new GeoPoint(stop.latitude, stop.longitude), screenPoint);
                boolean isSelected = selected != null && selected.id.equals(stop.id);
                int cellX = (int) Math.floor(screenPoint.x / gridSize);
                int cellY = (int) Math.floor(screenPoint.y / gridSize);
                long cell = (((long) cellX) << 32) ^ (cellY & 0xffffffffL);
                if (!isSelected && !occupiedCells.add(cell)) continue;
                occupiedCells.add(cell);
                if (zoom < 14.0) {
                    paint.setColor(isSelected ? markerColor : ContextCompat.getColor(
                            StopScheduleActivity.this, R.color.primary_teal));
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(screenPoint.x, screenPoint.y,
                            isSelected ? dotRadius * 1.8f : dotRadius, paint);
                    continue;
                }
                float radius = isSelected ? markerRadius + 2f * density : markerRadius;
                paint.setColor(Color.WHITE);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(screenPoint.x, screenPoint.y, radius, paint);
                paint.setColor(markerColor);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f * density);
                canvas.drawCircle(screenPoint.x, screenPoint.y, radius, paint);
                paint.setStyle(Paint.Style.FILL);
                if (stopIcon != null) {
                    int drawableSize = Math.round((isSelected ? iconSize + 4f * density : iconSize));
                    stopIcon.setBounds(screenPoint.x - drawableSize / 2,
                            screenPoint.y - drawableSize / 2,
                            screenPoint.x + drawableSize / 2,
                            screenPoint.y + drawableSize / 2);
                    stopIcon.draw(canvas);
                }
            }
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent event, MapView mapView) {
            if (mapView.getZoomLevelDouble() < 12.0) {
                binding.textArrivalStatus.setText(R.string.gtfs_zoom_map_hint);
                return true;
            }
            GtfsTransitStore.Stop closest = null;
            double zoom = mapView.getZoomLevelDouble();
            double closestDistance = Math.max(14d, 25d - (zoom - 12d) * 1.5d)
                    * getResources().getDisplayMetrics().density;
            double north = mapView.getBoundingBox().getLatNorth();
            double south = mapView.getBoundingBox().getLatSouth();
            double east = mapView.getBoundingBox().getLonEast();
            double west = mapView.getBoundingBox().getLonWest();
            for (GtfsTransitStore.Stop stop : stops) {
                if (stop.latitude > north || stop.latitude < south
                        || stop.longitude > east || stop.longitude < west) continue;
                mapView.getProjection().toPixels(new GeoPoint(stop.latitude, stop.longitude), screenPoint);
                double distance = Math.hypot(event.getX() - screenPoint.x, event.getY() - screenPoint.y);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = stop;
                }
            }
            if (closest != null) selectStop(closest);
            return closest != null;
        }

        private void invalidateMap() {
            if (binding != null) binding.mapView.invalidate();
        }
    }
}
