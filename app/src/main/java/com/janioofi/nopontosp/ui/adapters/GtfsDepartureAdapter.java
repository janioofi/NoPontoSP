package com.janioofi.nopontosp.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.janioofi.nopontosp.R;
import com.janioofi.nopontosp.repository.GtfsTransitStore;

import java.util.ArrayList;
import java.util.List;

public final class GtfsDepartureAdapter
        extends RecyclerView.Adapter<GtfsDepartureAdapter.DepartureViewHolder> {

    private final boolean arrivalMode;
    private final List<GtfsTransitStore.Departure> items = new ArrayList<>();

    public GtfsDepartureAdapter(boolean arrivalMode) {
        this.arrivalMode = arrivalMode;
    }

    public void submitList(List<GtfsTransitStore.Departure> departures) {
        items.clear();
        if (departures != null) items.addAll(departures);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DepartureViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gtfs_departure, parent, false);
        return new DepartureViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DepartureViewHolder holder, int position) {
        GtfsTransitStore.Departure departure = items.get(position);
        holder.time.setText(GtfsTransitStore.formatTime(departure.timeSeconds));
        holder.route.setText(departure.route == null ? "" : departure.route.shortName);
        holder.detail.setText(holder.itemView.getContext().getString(
                arrivalMode ? R.string.gtfs_arrival_direction : R.string.gtfs_departure_direction,
                departure.origin, departure.destination));
        holder.description.setText(holder.itemView.getContext().getString(
                arrivalMode ? R.string.gtfs_arrival_at_stop : R.string.gtfs_departure_long_name,
                arrivalMode ? departure.stopName
                        : (departure.route == null ? "" : departure.route.longName)));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static final class DepartureViewHolder extends RecyclerView.ViewHolder {
        final TextView time;
        final TextView route;
        final TextView detail;
        final TextView description;

        DepartureViewHolder(@NonNull View itemView) {
            super(itemView);
            time = itemView.findViewById(R.id.textScheduleTime);
            route = itemView.findViewById(R.id.textScheduleRoute);
            detail = itemView.findViewById(R.id.textScheduleDirection);
            description = itemView.findViewById(R.id.textScheduleDescription);
        }
    }
}
