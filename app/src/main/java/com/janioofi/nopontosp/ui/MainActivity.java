package com.janioofi.nopontosp.ui;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.janioofi.nopontosp.BuildConfig;
import com.janioofi.nopontosp.R;
import com.janioofi.nopontosp.databinding.ActivityMainBinding;
import com.janioofi.nopontosp.domain.Linha;
import com.janioofi.nopontosp.repository.LocalTransitStore;
import com.janioofi.nopontosp.repository.OlhoVivoRepository;
import com.janioofi.nopontosp.repository.RepositoryCallback;
import com.janioofi.nopontosp.ui.adapters.LinhaAdapter;

import java.util.List;

/** Tela inicial: busca de linha por nome/número, usando GET /Linha/Buscar. */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private LinhaAdapter adapter;
    private PageController<Linha> pager;
    private OlhoVivoRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = OlhoVivoRepository.getInstance(this);
        binding.textAppVersion.setText(getString(R.string.format_app_version, BuildConfig.VERSION_NAME));
        binding.toolbar.setNavigationOnClickListener(v ->
                binding.drawerLayout.openDrawer(GravityCompat.START));
        binding.navigationView.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            if (item.getItemId() == R.id.action_favorites) {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(this, FavoritesActivity.class));
                return true;
            }
            if (item.getItemId() == R.id.action_nearby_stops) {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(this, NearbyStopsActivity.class));
                return true;
            }
            return false;
        });

        adapter = new LinhaAdapter(this::abrirDetalheLinha);
        binding.recyclerLinhas.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerLinhas.setAdapter(adapter);
        pager = new PageController<>(binding.recyclerLinhas, adapter::submitList);

        binding.btnBuscar.setOnClickListener(v -> {
            fecharTeclado();
            realizarBusca();
        });
        binding.editBuscaLinha.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                fecharTeclado();
                realizarBusca();
                return true;
            }
            return false;
        });

        binding.searchTabs.addTab(binding.searchTabs.newTab().setText(R.string.tab_lines));
        binding.searchTabs.addTab(binding.searchTabs.newTab().setText(R.string.tab_stops));
        binding.searchTabs.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                if (tab.getPosition() == 1) {
                    startActivity(new Intent(MainActivity.this, ParadaSearchActivity.class));
                }
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });
        binding.btnOpenFavorites.setOnClickListener(v ->
                startActivity(new Intent(this, FavoritesActivity.class)));
        binding.btnNearbyStops.setOnClickListener(v ->
                startActivity(new Intent(this, NearbyStopsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding.searchTabs.getSelectedTabPosition() == 1) {
            binding.searchTabs.selectTab(binding.searchTabs.getTabAt(0));
        }
        renderSearchHistory();
    }

    private int searchGeneration;

    private void fecharTeclado() {
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(binding.editBuscaLinha.getWindowToken(), 0);
        }
        binding.editBuscaLinha.clearFocus();
    }

    private void realizarBusca() {
        String termos = binding.editBuscaLinha.getText() != null
                ? binding.editBuscaLinha.getText().toString().trim()
                : "";
        if (termos.isEmpty()) {
            return;
        }

        LocalTransitStore.recordSearch(this, LocalTransitStore.TYPE_LINE, termos);
        renderSearchHistory();
        mostrarCarregando(true);
        final int generation = ++searchGeneration;
        pager.setItems(null);
        repository.buscarLinhas(termos, new RepositoryCallback<List<Linha>>() {
            @Override
            public void onSuccess(List<Linha> result) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != searchGeneration) return;
                    mostrarCarregando(false);
                    pager.setItems(result);
                    if (result.isEmpty()) {
                        mostrarMensagem(getString(R.string.msg_sem_resultados));
                    } else {
                        esconderMensagem();
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != searchGeneration) return;
                    mostrarCarregando(false);
                    pager.setItems(null);
                    mostrarMensagem(message);
                });
            }
        });
    }

    private void abrirDetalheLinha(Linha linha) {
        Intent intent = new Intent(this, LinhaDetailActivity.class);
        intent.putExtra(LinhaDetailActivity.EXTRA_CODIGO_LINHA, linha.codigoLinha);
        intent.putExtra(LinhaDetailActivity.EXTRA_LETREIRO, linha.letreiro);
        intent.putExtra(LinhaDetailActivity.EXTRA_SENTIDO, linha.sentido);
        intent.putExtra(LinhaDetailActivity.EXTRA_DESTINO_SENTIDO, linha.destinoPorSentido());
        startActivity(intent);
    }

    private void mostrarCarregando(boolean carregando) {
        binding.progressBar.setVisibility(carregando ? View.VISIBLE : View.GONE);
        if (carregando) {
            esconderMensagem();
        }
    }

    private void mostrarMensagem(String mensagem) {
        binding.textMensagem.setText(mensagem);
        binding.textMensagem.setVisibility(View.VISIBLE);
    }

    private void esconderMensagem() {
        binding.textMensagem.setVisibility(View.GONE);
    }

    private void renderSearchHistory() {
        binding.recentLineSearches.removeAllViews();
        for (String query : LocalTransitStore.recentSearches(this, LocalTransitStore.TYPE_LINE)) {
            Chip chip = new Chip(this);
            chip.setText(query);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> {
                binding.editBuscaLinha.setText(query);
                realizarBusca();
            });
            binding.recentLineSearches.addView(chip);
        }
    }
}
