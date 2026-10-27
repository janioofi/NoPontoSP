package com.example.olhovivosp.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.olhovivosp.R;
import com.example.olhovivosp.domain.Parada;

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
        holder.itemView.setOnClickListener(v -> listener.onParadaClick(parada));
    }

    @Override
    public int getItemCount() {
        return paradas.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textNome;
        final TextView textEndereco;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textNome = itemView.findViewById(R.id.textNomeParada);
            textEndereco = itemView.findViewById(R.id.textEnderecoParada);
        }
    }
}
