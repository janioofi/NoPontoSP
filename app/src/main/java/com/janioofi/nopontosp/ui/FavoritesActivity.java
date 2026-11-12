package com.janioofi.nopontosp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.janioofi.nopontosp.R;
import com.janioofi.nopontosp.databinding.ActivityFavoritesBinding;
import com.janioofi.nopontosp.domain.Linha;
import com.janioofi.nopontosp.domain.Parada;
import com.janioofi.nopontosp.repository.LocalTransitStore;

import java.util.List;

public final class FavoritesActivity extends AppCompatActivity {
    private ActivityFavoritesBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFavoritesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderFavorites();
    }

    private void renderFavorites() {
        LinearLayout list = binding.favoriteList;
        list.removeAllViews();
        List<Linha> lines = LocalTransitStore.favoriteLines(this);
        List<Parada> stops = LocalTransitStore.favoriteStops(this);
        if (lines.isEmpty() && stops.isEmpty()) {
            addText(getString(R.string.favorites_empty), R.color.text_secondary, 14);
            return;
        }
        if (!lines.isEmpty()) {
            addText(getString(R.string.favorites_lines), R.color.text_primary, 18);
            for (Linha line : lines) addLine(line);
        }
        if (!stops.isEmpty()) {
            addText(getString(R.string.favorites_stops), R.color.text_primary, 18);
            for (Parada stop : stops) addStop(stop);
        }
    }

    private void addText(String value, int color, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(getColor(color));
        text.setTextSize(size);
        text.setPadding(0, 12, 0, 8);
        binding.favoriteList.addView(text);
    }

    private MaterialCardView createCard() {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Math.round(8 * getResources().getDisplayMetrics().density);
        card.setLayoutParams(params);
        card.setRadius(16 * getResources().getDisplayMetrics().density);
        card.setCardBackgroundColor(getColor(R.color.surface_white));
        card.setClickable(true);
        card.setFocusable(true);
        card.setUseCompatPadding(true);
        return card;
    }

    private void addLine(Linha line) {
        MaterialCardView card = createCard();
        TextView text = new TextView(this);
        text.setText(line.letreiro + "\n" + line.origemPorSentido() + " → " + line.destinoPorSentido());
        text.setTextColor(getColor(R.color.text_primary));
        text.setTextSize(15);
        text.setPadding(16, 16, 16, 16);
        card.addView(text);
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, LinhaDetailActivity.class);
            intent.putExtra(LinhaDetailActivity.EXTRA_CODIGO_LINHA, line.codigoLinha);
            intent.putExtra(LinhaDetailActivity.EXTRA_LETREIRO, line.letreiro);
            intent.putExtra(LinhaDetailActivity.EXTRA_SENTIDO, line.sentido);
            intent.putExtra(LinhaDetailActivity.EXTRA_DESTINO_SENTIDO, line.destinoPorSentido());
            startActivity(intent);
        });
        card.setOnLongClickListener(v -> {
            LocalTransitStore.toggleLine(this, line);
            renderFavorites();
            return true;
        });
        binding.favoriteList.addView(card);
    }

    private void addStop(Parada stop) {
        MaterialCardView card = createCard();
        TextView text = new TextView(this);
        text.setText(stop.nome + "\n" + stop.endereco);
        text.setTextColor(getColor(R.color.text_primary));
        text.setTextSize(15);
        text.setPadding(16, 16, 16, 16);
        card.addView(text);
        card.setOnClickListener(v -> openStop(stop));
        card.setOnLongClickListener(v -> {
            LocalTransitStore.toggleStop(this, stop);
            renderFavorites();
            return true;
        });
        binding.favoriteList.addView(card);
    }

    private void openStop(Parada stop) {
        Intent intent = new Intent(this, MapaActivity.class);
        intent.putExtra(MapaActivity.EXTRA_CODIGO_PARADA, stop.codigoParada);
        intent.putExtra(MapaActivity.EXTRA_NOME_PARADA, stop.nome);
        intent.putExtra(MapaActivity.EXTRA_PARADA_LAT, stop.latitude);
        intent.putExtra(MapaActivity.EXTRA_PARADA_LNG, stop.longitude);
        startActivity(intent);
    }
}
