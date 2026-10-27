package com.example.olhovivosp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.olhovivosp.R;
import com.example.olhovivosp.databinding.ActivityLinhaDetailBinding;
import com.example.olhovivosp.domain.Parada;
import com.example.olhovivosp.repository.OlhoVivoRepository;
import com.example.olhovivosp.repository.RepositoryCallback;
import com.example.olhovivosp.ui.adapters.ParadaAdapter;

import java.util.List;

/** Mostra os terminais da linha selecionada e seus pontos de parada (GET /Parada/BuscarParadasPorLinha). */
public class LinhaDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CODIGO_LINHA = "extra_codigo_linha";
    public static final String EXTRA_LETREIRO = "extra_letreiro";
    public static final String EXTRA_SENTIDO = "extra_sentido";
    public static final String EXTRA_DESTINO_SENTIDO = "extra_destino_sentido";

    private ActivityLinhaDetailBinding binding;
    private ParadaAdapter adapter;
    private PageController<Parada> pager;
    private OlhoVivoRepository repository;

    private int codigoLinha;
    private int sentido;
    private String letreiro;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLinhaDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = OlhoVivoRepository.getInstance(this);

        codigoLinha = getIntent().getIntExtra(EXTRA_CODIGO_LINHA, -1);
        sentido = getIntent().getIntExtra(EXTRA_SENTIDO, -1);
        letreiro = getIntent().getStringExtra(EXTRA_LETREIRO);
        String destinoSentido = getIntent().getStringExtra(EXTRA_DESTINO_SENTIDO);

        binding.toolbar.setTitle(letreiro);
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
        binding.textLetreiro.setText(letreiro);
        binding.textDestinoSentido.setText(getString(
                R.string.format_sentido_destino,
                destinoSentido != null && !destinoSentido.trim().isEmpty()
                        ? destinoSentido
                        : getString(R.string.label_destino_indisponivel)));

        adapter = new ParadaAdapter(this::abrirPrevisaoDaParada);
        binding.recyclerParadas.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerParadas.setAdapter(adapter);
        pager = new PageController<>(binding.recyclerParadas, adapter::submitList);

        binding.btnVerMapa.setOnClickListener(v -> abrirMapa());

        carregarParadas();
    }

    private void carregarParadas() {
        if (codigoLinha == -1) {
            mostrarMensagem(getString(R.string.msg_erro_rede));
            return;
        }
        binding.progressBar.setVisibility(View.VISIBLE);
        repository.buscarParadasPorLinha(codigoLinha, new RepositoryCallback<List<Parada>>() {
            @Override
            public void onSuccess(List<Parada> result) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
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
                    binding.progressBar.setVisibility(View.GONE);
                    mostrarMensagem(message);
                });
            }
        });
    }

    private void abrirMapa() {
        Intent intent = new Intent(this, MapaActivity.class);
        intent.putExtra(MapaActivity.EXTRA_CODIGO_LINHA, codigoLinha);
        intent.putExtra(MapaActivity.EXTRA_SENTIDO, sentido);
        intent.putExtra(MapaActivity.EXTRA_LETREIRO, letreiro);
        intent.putExtra(MapaActivity.EXTRA_DESTINO_SENTIDO,
                getIntent().getStringExtra(EXTRA_DESTINO_SENTIDO));
        startActivity(intent);
    }

    private void abrirPrevisaoDaParada(Parada parada) {
        Intent intent = new Intent(this, MapaActivity.class);
        intent.putExtra(MapaActivity.EXTRA_CODIGO_LINHA, codigoLinha);
        intent.putExtra(MapaActivity.EXTRA_SENTIDO, sentido);
        intent.putExtra(MapaActivity.EXTRA_LETREIRO, letreiro);
        intent.putExtra(MapaActivity.EXTRA_DESTINO_SENTIDO,
                getIntent().getStringExtra(EXTRA_DESTINO_SENTIDO));
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
