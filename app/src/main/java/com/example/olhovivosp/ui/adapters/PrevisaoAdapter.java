package com.example.olhovivosp.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.olhovivosp.R;
import com.example.olhovivosp.domain.Previsao;

import java.util.ArrayList;
import java.util.List;

public class PrevisaoAdapter extends RecyclerView.Adapter<PrevisaoAdapter.ViewHolder> {

    private final List<Previsao> previsoes = new ArrayList<>();

    public void submitList(List<Previsao> novasPrevisoes) {
        previsoes.clear();
        if (novasPrevisoes != null) {
            previsoes.addAll(novasPrevisoes);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_previsao, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Previsao previsao = previsoes.get(position);
        holder.textLinha.setText(previsao.letreiroLinha + " (prefixo " + previsao.prefixoVeiculo + ")");
        holder.textHorario.setText(previsao.horarioPrevisto + (previsao.acessivel ? " • Acessível" : ""));
    }

    @Override
    public int getItemCount() {
        return previsoes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textLinha;
        final TextView textHorario;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textLinha = itemView.findViewById(R.id.textPrevisaoLinha);
            textHorario = itemView.findViewById(R.id.textPrevisaoHorario);
        }
    }
}
