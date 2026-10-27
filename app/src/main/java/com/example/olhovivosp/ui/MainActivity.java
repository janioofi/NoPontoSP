package com.example.olhovivosp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.olhovivosp.R;
import com.example.olhovivosp.databinding.ActivityMainBinding;
import com.example.olhovivosp.domain.Linha;
import com.example.olhovivosp.repository.OlhoVivoRepository;
import com.example.olhovivosp.repository.RepositoryCallback;
import com.example.olhovivosp.ui.adapters.LinhaAdapter;

import java.util.List;

/** Tela inicial: busca de linha por nome/número, usando GET /Linha/Buscar. */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private LinhaAdapter adapter;
    private PageController<Linha> pager;
    private final OlhoVivoRepository repository = OlhoVivoRepository.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new LinhaAdapter(this::abrirDetalheLinha);
        binding.recyclerLinhas.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerLinhas.setAdapter(adapter);
        pager = new PageController<>(binding.recyclerLinhas, adapter::submitList);

        binding.btnBuscar.setOnClickListener(v -> realizarBusca());
        binding.editBuscaLinha.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                realizarBusca();
                return true;
            }
            return false;
        });

        binding.btnBuscarParada.setOnClickListener(v ->
                startActivity(new Intent(this, ParadaSearchActivity.class)));
    }

    private int searchGeneration;

    private void realizarBusca() {
        String termos = binding.editBuscaLinha.getText() != null
                ? binding.editBuscaLinha.getText().toString().trim()
                : "";
        if (termos.isEmpty()) {
            return;
        }

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
}
