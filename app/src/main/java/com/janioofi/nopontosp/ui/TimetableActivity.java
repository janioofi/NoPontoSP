package com.janioofi.nopontosp.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.ChipGroup;
import com.janioofi.nopontosp.R;
import com.janioofi.nopontosp.databinding.ActivityTimetableBinding;
import com.janioofi.nopontosp.repository.GtfsTransitStore;
import com.janioofi.nopontosp.ui.adapters.GtfsDepartureAdapter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TimetableActivity extends AppCompatActivity {

    private static final int DEPARTURES_PAGE_SIZE = 15;
    private ActivityTimetableBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private GtfsTransitStore store;
    private PageController<GtfsTransitStore.Departure> pager;
    private final List<GtfsTransitStore.Route> routeOptions = new ArrayList<>();
    private List<GtfsTransitStore.Direction> directions = new ArrayList<>();
    private GtfsTransitStore.Route selectedRoute;
    private GtfsTransitStore.DayType selectedDayType = GtfsTransitStore.DayType.WEEKDAY;
    private GtfsTransitStore.TimePeriod selectedPeriod = GtfsTransitStore.TimePeriod.ALL;
    private int queryGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTimetableBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        GtfsDepartureAdapter adapter = new GtfsDepartureAdapter(false);
        binding.recyclerTimetable.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTimetable.setAdapter(adapter);
        pager = new PageController<>(binding.recyclerTimetable, adapter::submitList,
                DEPARTURES_PAGE_SIZE);

        setupServiceDay();
        setupDirection();
        setupPeriodFilter();
        binding.editRouteSearch.setThreshold(1);
        binding.editRouteSearch.setOnItemClickListener((parent, view, position, id) -> {
            Object selectedItem = parent.getItemAtPosition(position);
            if (!(selectedItem instanceof GtfsTransitStore.Route)) {
                queryGeneration++;
                selectedRoute = null;
                directions = new ArrayList<>();
                pager.setItems(null);
                binding.textTimetableStatus.setText(R.string.gtfs_route_selection_error);
                return;
            }
            queryGeneration++;
            selectedRoute = (GtfsTransitStore.Route) selectedItem;
            showDirections();
        });
        binding.editRouteSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable value) {
                if (selectedRoute != null && !selectedRoute.toString().contentEquals(value)) {
                    queryGeneration++;
                    selectedRoute = null;
                    directions = new ArrayList<>();
                    binding.spinnerDirection.setAdapter(emptyStringAdapter());
                    pager.setItems(null);
                    binding.textTimetableStatus.setText(R.string.gtfs_choose_route);
                }
            }
        });

        executor.execute(() -> {
            try {
                GtfsTransitStore loaded = GtfsTransitStore.load(getAssets());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    store = loaded;
                    routeOptions.clear();
                    routeOptions.addAll(loaded.getRoutes());
                    ArrayAdapter<GtfsTransitStore.Route> routeAdapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_dropdown_item_1line, routeOptions);
                    binding.editRouteSearch.setAdapter(routeAdapter);
                    binding.progressTimetable.setVisibility(View.GONE);
                    binding.textTimetableStatus.setText(R.string.gtfs_choose_route);
                });
            } catch (Exception error) {
                android.util.Log.e("TimetableActivity", "Não foi possível carregar o GTFS.", error);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    binding.progressTimetable.setVisibility(View.GONE);
                    binding.textTimetableStatus.setText(R.string.gtfs_data_error);
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        queryGeneration++;
        executor.shutdownNow();
        super.onDestroy();
    }

    private void setupServiceDay() {
        String[] dayLabels = getResources().getStringArray(R.array.gtfs_service_days);
        ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, dayLabels);
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerServiceDay.setAdapter(dayAdapter);
        GtfsTransitStore.DayType today =
                GtfsTransitStore.dayTypeForCalendarDay(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
        binding.spinnerServiceDay.setSelection(today.ordinal());
        binding.spinnerServiceDay.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedDayType = GtfsTransitStore.DayType.values()[position];
                loadDepartures();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupDirection() {
        binding.spinnerDirection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadDepartures();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        binding.spinnerDirection.setAdapter(emptyStringAdapter());
    }

    private void setupPeriodFilter() {
        binding.timePeriodGroup.setOnCheckedStateChangeListener((ChipGroup group, List<Integer> checkedIds) -> {
            int selected = checkedIds.isEmpty() ? R.id.chipAllDay : checkedIds.get(0);
            if (selected == R.id.chipDawn) selectedPeriod = GtfsTransitStore.TimePeriod.DAWN;
            else if (selected == R.id.chipMorning) selectedPeriod = GtfsTransitStore.TimePeriod.MORNING;
            else if (selected == R.id.chipAfternoon) selectedPeriod = GtfsTransitStore.TimePeriod.AFTERNOON;
            else if (selected == R.id.chipEvening) selectedPeriod = GtfsTransitStore.TimePeriod.EVENING;
            else selectedPeriod = GtfsTransitStore.TimePeriod.ALL;
            loadDepartures();
        });
    }

    private void showDirections() {
        if (store == null || selectedRoute == null) return;
        directions = store.getDirections(selectedRoute.id);
        List<String> labels = new ArrayList<>();
        for (GtfsTransitStore.Direction direction : directions) labels.add(direction.label);
        ArrayAdapter<String> directionAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        directionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerDirection.setAdapter(directionAdapter);
        if (directions.isEmpty()) {
            binding.textTimetableStatus.setText(R.string.gtfs_no_directions);
            pager.setItems(null);
        } else {
            binding.spinnerDirection.setSelection(0);
            loadDepartures();
        }
    }

    private void loadDepartures() {
        if (store == null || selectedRoute == null || directions.isEmpty()) return;
        int position = binding.spinnerDirection.getSelectedItemPosition();
        if (position < 0 || position >= directions.size()) return;
        String routeId = selectedRoute.id;
        String routeShortName = selectedRoute.shortName;
        String directionKey = directions.get(position).key;
        GtfsTransitStore.DayType dayType = selectedDayType;
        GtfsTransitStore.TimePeriod period = selectedPeriod;
        int generation = ++queryGeneration;
        binding.progressTimetable.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            List<GtfsTransitStore.Departure> departures =
                    store.getDepartures(routeId, directionKey, dayType, period);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != queryGeneration) return;
                binding.progressTimetable.setVisibility(View.GONE);
                pager.setItems(departures);
                if (departures.isEmpty()) {
                    binding.textTimetableStatus.setText(getString(
                            R.string.gtfs_route_no_departures, routeShortName));
                } else {
                    binding.textTimetableStatus.setText(getString(
                            R.string.gtfs_route_departures_count,
                            routeShortName, departures.size()));
                }
            });
        });
    }

    private ArrayAdapter<String> emptyStringAdapter() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new ArrayList<>());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }
}
