package com.janioofi.nopontosp.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.janioofi.nopontosp.R;
import com.janioofi.nopontosp.domain.Linha;
import com.janioofi.nopontosp.repository.LocalTransitStore;

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
        holder.textLetreiro.setText(linha.letreiro);
        holder.textItinerario.setText(linha.origemPorSentido() + " → " + linha.destinoPorSentido());
        String destino = linha.destinoPorSentido();
        String sentidoTexto = destino == null || destino.trim().isEmpty()
                ? holder.itemView.getContext().getString(R.string.label_destino_indisponivel)
                : holder.itemView.getContext().getString(R.string.format_sentido_destino, destino);
        holder.textSentido.setText(sentidoTexto);
        holder.btnFavorite.setImageResource(LocalTransitStore.isLineFavorite(
                holder.itemView.getContext(), linha) ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
        holder.btnFavorite.setContentDescription(holder.itemView.getContext().getString(
                LocalTransitStore.isLineFavorite(holder.itemView.getContext(), linha)
                        ? R.string.action_remove_favorite : R.string.action_add_favorite));
        holder.btnFavorite.setOnClickListener(v -> {
            LocalTransitStore.toggleLine(holder.itemView.getContext(), linha);
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) notifyItemChanged(adapterPosition);
        });
        holder.itemView.setOnClickListener(v -> listener.onLinhaClick(linha));
    }

    @Override
    public int getItemCount() {
        return linhas.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textLetreiro;
        final TextView textItinerario;
        final TextView textSentido;
        final ImageButton btnFavorite;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textLetreiro = itemView.findViewById(R.id.textLetreiro);
            textItinerario = itemView.findViewById(R.id.textItinerario);
            textSentido = itemView.findViewById(R.id.textSentido);
            btnFavorite = itemView.findViewById(R.id.btnFavoriteLine);
        }
    }
}
