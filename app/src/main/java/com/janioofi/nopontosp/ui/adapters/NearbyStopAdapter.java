package com.janioofi.nopontosp.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.janioofi.nopontosp.R;
import com.janioofi.nopontosp.domain.Parada;
import com.janioofi.nopontosp.repository.LocalTransitStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NearbyStopAdapter extends RecyclerView.Adapter<NearbyStopAdapter.ViewHolder> {
    public static final class Item {
        public final Parada parada;
        public final float distanceMeters;

        public Item(Parada parada, float distanceMeters) {
            this.parada = parada;
            this.distanceMeters = distanceMeters;
        }
    }

    public interface Listener {
        void onStopSelected(Parada stop);
    }

    private final List<Item> items = new ArrayList<>();
    private final Listener listener;

    public NearbyStopAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Item> values) {
        items.clear();
        if (values != null) items.addAll(values);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_nearby_stop, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Item item = items.get(position);
        holder.name.setText(item.parada.nome);
        String distance = item.distanceMeters < 1000
                ? String.format(Locale.getDefault(), "%d m", Math.round(item.distanceMeters))
                : String.format(Locale.getDefault(), "%.1f km", item.distanceMeters / 1000f);
        holder.distance.setText(holder.itemView.getContext().getString(R.string.nearby_distance, distance));
        boolean favorite = LocalTransitStore.isStopFavorite(holder.itemView.getContext(), item.parada);
        holder.favorite.setImageResource(favorite
                ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
        holder.favorite.setContentDescription(holder.itemView.getContext().getString(
                favorite ? R.string.action_remove_favorite : R.string.action_add_favorite));
        holder.favorite.setOnClickListener(v -> {
            LocalTransitStore.toggleStop(holder.itemView.getContext(), item.parada);
            int current = holder.getBindingAdapterPosition();
            if (current != RecyclerView.NO_POSITION) notifyItemChanged(current);
        });
        holder.itemView.setOnClickListener(v -> listener.onStopSelected(item.parada));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView distance;
        final ImageButton favorite;

        ViewHolder(@NonNull View view) {
            super(view);
            name = view.findViewById(R.id.textNearbyName);
            distance = view.findViewById(R.id.textNearbyDistance);
            favorite = view.findViewById(R.id.btnFavoriteNearby);
        }
    }
}
