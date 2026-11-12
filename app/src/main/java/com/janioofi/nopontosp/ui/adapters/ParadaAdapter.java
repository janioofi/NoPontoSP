package com.janioofi.nopontosp.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.janioofi.nopontosp.R;
import com.janioofi.nopontosp.domain.Parada;
import com.janioofi.nopontosp.repository.LocalTransitStore;

import java.util.ArrayList;
import java.util.List;

public class ParadaAdapter extends RecyclerView.Adapter<ParadaAdapter.ViewHolder> {

    public interface OnParadaClickListener {
        void onParadaClick(Parada parada);
    }

    private final List<Parada> paradas = new ArrayList<>();
    private final OnParadaClickListener listener;

    public ParadaAdapter(OnParadaClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Parada> novasParadas) {
        paradas.clear();
        if (novasParadas != null) {
            paradas.addAll(novasParadas);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_parada, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Parada parada = paradas.get(position);
        holder.textNome.setText(parada.nome);
        holder.textEndereco.setText(parada.endereco);
        boolean favorite = LocalTransitStore.isStopFavorite(holder.itemView.getContext(), parada);
        holder.btnFavorite.setImageResource(favorite
                ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
        holder.btnFavorite.setContentDescription(holder.itemView.getContext().getString(
                favorite ? R.string.action_remove_favorite : R.string.action_add_favorite));
        holder.btnFavorite.setOnClickListener(v -> {
            LocalTransitStore.toggleStop(holder.itemView.getContext(), parada);
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) notifyItemChanged(adapterPosition);
        });
        holder.itemView.setOnClickListener(v -> listener.onParadaClick(parada));
    }

    @Override
    public int getItemCount() {
        return paradas.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textNome;
        final TextView textEndereco;
        final ImageButton btnFavorite;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textNome = itemView.findViewById(R.id.textNomeParada);
            textEndereco = itemView.findViewById(R.id.textEnderecoParada);
            btnFavorite = itemView.findViewById(R.id.btnFavoriteStop);
        }
    }
}
