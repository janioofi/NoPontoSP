package com.example.olhovivosp.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.olhovivosp.R;
import com.example.olhovivosp.domain.Linha;

import java.util.ArrayList;
import java.util.List;

public class LinhaAdapter extends RecyclerView.Adapter<LinhaAdapter.ViewHolder> {

    public interface OnLinhaClickListener {
        void onLinhaClick(Linha linha);
    }

    private final List<Linha> linhas = new ArrayList<>();
    private final OnLinhaClickListener listener;

    public LinhaAdapter(OnLinhaClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Linha> novasLinhas) {
        linhas.clear();
        if (novasLinhas != null) {
            linhas.addAll(novasLinhas);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_linha, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Linha linha = linhas.get(position);
        holder.textLetreiro.setText(linha.descricaoCompleta());
        String destino = linha.destinoPorSentido();
        String sentidoTexto = destino == null || destino.trim().isEmpty()
                ? holder.itemView.getContext().getString(R.string.label_destino_indisponivel)
                : holder.itemView.getContext().getString(R.string.format_sentido_destino, destino);
        holder.textSentido.setText(sentidoTexto);
        holder.itemView.setOnClickListener(v -> listener.onLinhaClick(linha));
    }

    @Override
    public int getItemCount() {
        return linhas.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textLetreiro;
        final TextView textSentido;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textLetreiro = itemView.findViewById(R.id.textLetreiro);
            textSentido = itemView.findViewById(R.id.textSentido);
        }
    }
}
