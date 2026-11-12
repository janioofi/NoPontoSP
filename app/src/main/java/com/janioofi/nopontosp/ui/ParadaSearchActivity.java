package com.janioofi.nopontosp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.janioofi.nopontosp.R;
import com.janioofi.nopontosp.databinding.ActivityParadaSearchBinding;
import com.janioofi.nopontosp.domain.Parada;
import com.janioofi.nopontosp.repository.LocalTransitStore;
import com.janioofi.nopontosp.repository.OlhoVivoRepository;
import com.janioofi.nopontosp.repository.RepositoryCallback;
import com.janioofi.nopontosp.ui.adapters.ParadaAdapter;

import java.util.List;

/** Busca de parada por nome/endereço (GET /Parada/Buscar), independente de uma linha. */
public class ParadaSearchActivity extends AppCompatActivity {

    private ActivityParadaSearchBinding binding;
    private ParadaAdapter adapter;
    private PageController<Parada> pager;
    private OlhoVivoRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityParadaSearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = OlhoVivoRepository.getInstance(this);

        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
        binding.searchTabs.addTab(binding.searchTabs.newTab().setText(R.string.tab_lines));
        binding.searchTabs.addTab(binding.searchTabs.newTab().setText(R.string.tab_stops));
        binding.searchTabs.selectTab(binding.searchTabs.getTabAt(1));
        binding.searchTabs.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                if (tab.getPosition() == 0) finish();
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });
        renderSearchHistory();

        adapter = new ParadaAdapter(this::abrirPrevisaoDaParada);
        binding.recyclerParadas.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerParadas.setAdapter(adapter);
        pager = new PageController<>(binding.recyclerParadas, adapter::submitList);

        binding.btnBuscar.setOnClickListener(v -> {
            fecharTeclado();
            realizarBusca();
        });
        binding.editBuscaParada.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                fecharTeclado();
                realizarBusca();
                return true;
            }
            return false;
        });
    }

    private int searchGeneration;

    private void fecharTeclado() {
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(binding.editBuscaParada.getWindowToken(), 0);
        }
        binding.editBuscaParada.clearFocus();
    }

    private void realizarBusca() {
        String termos = binding.editBuscaParada.getText() != null
                ? binding.editBuscaParada.getText().toString().trim()
                : "";
        if (termos.isEmpty()) {
            return;
        }

        LocalTransitStore.recordSearch(this, LocalTransitStore.TYPE_STOP, termos);
        renderSearchHistory();
        binding.progressBar.setVisibility(View.VISIBLE);
        esconderMensagem();
        final int generation = ++searchGeneration;
        pager.setItems(null);
        repository.buscarParadas(termos, new RepositoryCallback<List<Parada>>() {
            @Override
            public void onSuccess(List<Parada> result) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != searchGeneration) return;
                    binding.progressBar.setVisibility(View.GONE);
                    pager.setItems(result);
                    if (result.isEmpty()) {
                        mostrarMensagem(getString(R.string.msg_sem_resultados));
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != searchGeneration) return;
                    binding.progressBar.setVisibility(View.GONE);
                    pager.setItems(null);
                    mostrarMensagem(message);
                });
            }
        });
    }

    private void abrirPrevisaoDaParada(Parada parada) {
        Intent intent = new Intent(this, MapaActivity.class);
        intent.putExtra(MapaActivity.EXTRA_CODIGO_PARADA, parada.codigoParada);
        intent.putExtra(MapaActivity.EXTRA_NOME_PARADA, parada.nome);
        intent.putExtra(MapaActivity.EXTRA_PARADA_LAT, parada.latitude);
        intent.putExtra(MapaActivity.EXTRA_PARADA_LNG, parada.longitude);
        startActivity(intent);
    }

    private void mostrarMensagem(String mensagem) {
        binding.textMensagem.setText(mensagem);
        binding.textMensagem.setVisibility(View.VISIBLE);
    }

    private void esconderMensagem() {
        binding.textMensagem.setVisibility(View.GONE);
    }

    private void renderSearchHistory() {
        binding.recentStopSearches.removeAllViews();
        for (String query : LocalTransitStore.recentSearches(this, LocalTransitStore.TYPE_STOP)) {
            Chip chip = new Chip(this);
            chip.setText(query);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> {
                binding.editBuscaParada.setText(query);
                realizarBusca();
            });
            binding.recentStopSearches.addView(chip);
        }
    }
}
