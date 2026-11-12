package com.janioofi.nopontosp.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.janioofi.nopontosp.R;
import com.janioofi.nopontosp.domain.Previsao;

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
        holder.textLinha.setText(previsao.letreiroLinha);
        holder.textPrefixo.setText(holder.itemView.getContext().getString(
                R.string.format_prefixo_veiculo, previsao.prefixoVeiculo));
        holder.textHorario.setText(previsao.horarioPrevisto);
        holder.iconAcessivel.setVisibility(previsao.acessivel ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return previsoes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textLinha;
        final TextView textPrefixo;
        final TextView textHorario;
        final ImageView iconAcessivel;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textLinha = itemView.findViewById(R.id.textPrevisaoLinha);
            textPrefixo = itemView.findViewById(R.id.textPrevisaoPrefixo);
            textHorario = itemView.findViewById(R.id.textPrevisaoHorario);
            iconAcessivel = itemView.findViewById(R.id.iconPrevisaoAcessivel);
        }
    }
}
