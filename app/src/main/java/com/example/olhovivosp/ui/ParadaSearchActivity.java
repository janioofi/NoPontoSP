package com.example.olhovivosp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.olhovivosp.R;
import com.example.olhovivosp.databinding.ActivityParadaSearchBinding;
import com.example.olhovivosp.domain.Parada;
import com.example.olhovivosp.repository.OlhoVivoRepository;
import com.example.olhovivosp.repository.RepositoryCallback;
import com.example.olhovivosp.ui.adapters.ParadaAdapter;

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

        adapter = new ParadaAdapter(this::abrirPrevisaoDaParada);
        binding.recyclerParadas.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerParadas.setAdapter(adapter);
        pager = new PageController<>(binding.recyclerParadas, adapter::submitList);

        binding.btnBuscar.setOnClickListener(v -> realizarBusca());
        binding.editBuscaParada.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                realizarBusca();
                return true;
            }
            return false;
        });
    }

    private int searchGeneration;

    private void realizarBusca() {
        String termos = binding.editBuscaParada.getText() != null
                ? binding.editBuscaParada.getText().toString().trim()
                : "";
        if (termos.isEmpty()) {
            return;
        }

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
}
