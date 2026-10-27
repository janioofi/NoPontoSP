package com.example.olhovivosp.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.olhovivosp.BuildConfig;
import com.example.olhovivosp.R;
import com.example.olhovivosp.databinding.ActivityMapaBinding;
import com.example.olhovivosp.domain.Parada;
import com.example.olhovivosp.domain.PrevisaoParada;
import com.example.olhovivosp.domain.Veiculo;
import com.example.olhovivosp.repository.OlhoVivoRepository;
import com.example.olhovivosp.repository.RepositoryCallback;
import com.example.olhovivosp.ui.adapters.PrevisaoAdapter;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Mapa com a posição em tempo real dos veículos de uma linha (GET /Posicao/Linha),
 * atualizado por polling. Se uma parada específica foi informada, também mostra a
 * previsão de chegada para ela (GET /Previsao/Parada).
 */
public class MapaActivity extends AppCompatActivity {

    public static final String EXTRA_CODIGO_LINHA = "extra_codigo_linha";
    public static final String EXTRA_SENTIDO = "extra_sentido";
    public static final String EXTRA_LETREIRO = "extra_letreiro";
    public static final String EXTRA_DESTINO_SENTIDO = "extra_destino_sentido";
    public static final String EXTRA_CODIGO_PARADA = "extra_codigo_parada";
    public static final String EXTRA_NOME_PARADA = "extra_nome_parada";
    public static final String EXTRA_PARADA_LAT = "extra_parada_lat";
    public static final String EXTRA_PARADA_LNG = "extra_parada_lng";

    private static final long INTERVALO_POLLING_MS = 10_000L;
    private static final int REQUEST_PERMISSAO_LOCALIZACAO = 1001;

    private ActivityMapaBinding binding;
    private PrevisaoAdapter previsaoAdapter;
    private PageController<com.example.olhovivosp.domain.Previsao> pager;
    private MyLocationNewOverlay localizacaoOverlay;
    private Polyline trajetoOverlay;
    private final OlhoVivoRepository repository = OlhoVivoRepository.getInstance();
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Marker> marcadoresPorPrefixo = new HashMap<>();

    private int codigoLinha;
    private int sentido = -1;
    private int codigoParada = -1;
    private String destinoSentido;
    private boolean positionInFlight;
    private boolean cameraAjustada;
    private boolean localizacaoAtivada;

    private final Runnable pollingRunnable = new Runnable() {
        @Override
        public void run() {
            atualizarPosicoes();
            pollingHandler.postDelayed(this, INTERVALO_POLLING_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(
                "OlhoVivoSP/" + BuildConfig.VERSION_NAME
                        + " (Android; package=" + getPackageName() + ")");
        if (BuildConfig.DEBUG) {
            Configuration.getInstance().setDebugMapTileDownloader(true);
        }
        binding = ActivityMapaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        codigoLinha = getIntent().getIntExtra(EXTRA_CODIGO_LINHA, -1);
        sentido = getIntent().getIntExtra(EXTRA_SENTIDO, -1);
        codigoParada = getIntent().getIntExtra(EXTRA_CODIGO_PARADA, -1);
        String letreiro = getIntent().getStringExtra(EXTRA_LETREIRO);
        destinoSentido = getIntent().getStringExtra(EXTRA_DESTINO_SENTIDO);
        binding.textInfoTrajeto.setVisibility(codigoLinha == -1 ? View.GONE : View.VISIBLE);

        if (letreiro != null && destinoSentido != null && !destinoSentido.trim().isEmpty()) {
            binding.toolbar.setTitle(getString(R.string.format_mapa_linha_destino, letreiro, destinoSentido));
        } else {
            binding.toolbar.setTitle(letreiro != null ? letreiro : getString(R.string.title_mapa));
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        binding.mapView.setTileSource(TileSourceFactory.MAPNIK);
        binding.mapView.setMultiTouchControls(true);
        binding.mapView.getOverlays().add(new CopyrightOverlay(this));
        localizacaoOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(this), binding.mapView);
        android.graphics.Bitmap location = locationBitmap();
        localizacaoOverlay.setPersonIcon(location);
        localizacaoOverlay.setDirectionIcon(location);
        localizacaoOverlay.setPersonAnchor(0.5f, 0.5f);
        localizacaoOverlay.setDirectionAnchor(0.5f, 0.5f);
        binding.mapView.getOverlays().add(localizacaoOverlay);
        binding.btnMinhaLocalizacao.setOnClickListener(v -> centralizarNaMinhaLocalizacao());

        double paradaLat = getIntent().getDoubleExtra(EXTRA_PARADA_LAT, Double.NaN);
        double paradaLng = getIntent().getDoubleExtra(EXTRA_PARADA_LNG, Double.NaN);
        if (Double.isFinite(paradaLat) && Double.isFinite(paradaLng)
                && paradaLat >= -90 && paradaLat <= 90
                && paradaLng >= -180 && paradaLng <= 180) {
            GeoPoint posicaoParada = new GeoPoint(paradaLat, paradaLng);
            Marker marcadorParada = new Marker(binding.mapView);
            marcadorParada.setPosition(posicaoParada);
            marcadorParada.setTitle(getIntent().getStringExtra(EXTRA_NOME_PARADA));
            marcadorParada.setOnMarkerClickListener((marker, map) -> { carregarPrevisaoDaParada(); return true; });
            marcadorParada.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            Drawable icone = DrawableCompat.wrap(getDrawable(org.osmdroid.library.R.drawable.marker_default)).mutate();
            DrawableCompat.setTint(icone, Color.RED);
            marcadorParada.setIcon(icone);
            binding.mapView.getOverlays().add(marcadorParada);
            cameraAjustada = true;
            binding.mapView.getController().setZoom(16.0);
            binding.mapView.getController().setCenter(posicaoParada);
        } else {
            binding.mapView.getController().setZoom(11.0);
            binding.mapView.getController().setCenter(new GeoPoint(-23.5505, -46.6333));
        }

        previsaoAdapter = new PrevisaoAdapter();
        binding.recyclerPrevisoes.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPrevisoes.setAdapter(previsaoAdapter);
        pager = new PageController<>(binding.recyclerPrevisoes, previsaoAdapter::submitList);

        if (codigoParada != -1) {
            String nomeParada = getIntent().getStringExtra(EXTRA_NOME_PARADA);
            binding.textVeiculoCount.setText(nomeParada != null ? nomeParada : "");
            if (codigoLinha == -1) {
                binding.textStatus.setText("");
            }
            carregarPrevisaoDaParada();
        }
        if (codigoLinha != -1) {
            carregarTrajetoDaLinha();
        }
        solicitarPermissaoLocalizacaoSeNecessario();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.mapView.onResume();
        ativarLocalizacaoSePermitida();
        if (codigoLinha != -1) {
            pollingHandler.post(pollingRunnable);
        }
    }

    @Override
    protected void onPause() {
        pollingHandler.removeCallbacks(pollingRunnable);
        if (localizacaoAtivada) {
            localizacaoOverlay.disableMyLocation();
            localizacaoAtivada = false;
        }
        binding.mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        geometryWorker.shutdownNow();
        binding.mapView.onDetach();
        super.onDestroy();
    }

    private final java.util.concurrent.ExecutorService geometryWorker =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    private void carregarTrajetoDaLinha() {
        String route = getIntent().getStringExtra(EXTRA_LETREIRO);
        geometryWorker.execute(() -> {
            List<GeoPoint> points;
            try {
                points = com.example.olhovivosp.repository.GtfsGeometry.load(getAssets(), route, sentido, destinoSentido);
            } catch (Exception error) { points = java.util.Collections.emptyList(); }
            final List<GeoPoint> geometry = points;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (geometry.size() < 2) {
                    binding.textInfoTrajeto.setText("Trajeto GTFS indisponível para este sentido");
                    return;
                }
                trajetoOverlay = new Polyline(binding.mapView);
                trajetoOverlay.setPoints(geometry);
                trajetoOverlay.setColor(ContextCompat.getColor(this, R.color.sp_trans_red));
                trajetoOverlay.setWidth(8f);
                binding.mapView.getOverlays().add(1, trajetoOverlay);
                binding.textInfoTrajeto.setText("Trajeto GTFS • sentido selecionado");
                if (!cameraAjustada) {
                    binding.mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(geometry), true, 80);
                    cameraAjustada = true;
                }
                binding.mapView.invalidate();
            });
        });
    }

    private android.graphics.Bitmap locationBitmap() {
        int size = (int) (40 * getResources().getDisplayMetrics().density);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        float center = size / 2f;
        paint.setColor(0x331565C0); canvas.drawCircle(center, center, size * .48f, paint);
        paint.setColor(Color.WHITE); canvas.drawCircle(center, center, size * .29f, paint);
        paint.setColor(0xFF1565C0); canvas.drawCircle(center, center, size * .21f, paint);
        return bitmap;
    }

    private void solicitarPermissaoLocalizacaoSeNecessario() {
        if (!temPermissaoLocalizacao()) {
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQUEST_PERMISSAO_LOCALIZACAO);
        }
    }

    private boolean temPermissaoLocalizacao() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void ativarLocalizacaoSePermitida() {
        if (temPermissaoLocalizacao() && !localizacaoAtivada) {
            localizacaoAtivada = localizacaoOverlay.enableMyLocation();
        }
    }

    private void centralizarNaMinhaLocalizacao() {
        if (!temPermissaoLocalizacao()) {
            solicitarPermissaoLocalizacaoSeNecessario();
            return;
        }
        ativarLocalizacaoSePermitida();
        GeoPoint localizacao = localizacaoOverlay.getMyLocation();
        if (localizacao == null) {
            Toast.makeText(this, R.string.msg_localizacao_indisponivel, Toast.LENGTH_LONG).show();
            return;
        }
        binding.mapView.getController().setZoom(Math.max(binding.mapView.getZoomLevelDouble(), 15.0));
        binding.mapView.getController().animateTo(localizacao);
        cameraAjustada = true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSAO_LOCALIZACAO) {
            if (temPermissaoLocalizacao()) {
                ativarLocalizacaoSePermitida();
            } else {
                Toast.makeText(this, R.string.msg_localizacao_indisponivel, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void atualizarPosicoes() {
        if (codigoLinha == -1 || positionInFlight) return;
        positionInFlight = true;
        repository.posicaoPorLinha(codigoLinha, sentido, destinoSentido,
                new RepositoryCallback<List<Veiculo>>() {
            @Override
            public void onSuccess(List<Veiculo> veiculos) {
                runOnUiThread(() -> {
                    positionInFlight = false;
                    if (!isFinishing() && !isDestroyed()) desenharVeiculos(veiculos);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    positionInFlight = false;
                    if (!isFinishing() && !isDestroyed()) binding.textStatus.setText(message);
                });
            }
        });
    }

    private void desenharVeiculos(List<Veiculo> veiculos) {
        binding.textStatus.setText(veiculos.isEmpty()
                ? R.string.msg_sem_veiculos_linha
                : R.string.msg_atualizando_posicoes);
        binding.textVeiculoCount.setText(getString(R.string.format_veiculo_count, veiculos.size()));

        Set<String> prefixosAtuais = new HashSet<>();
        List<GeoPoint> pontos = new ArrayList<>();
        for (Veiculo veiculo : veiculos) {
            if (!Double.isFinite(veiculo.latitude) || !Double.isFinite(veiculo.longitude)
                    || veiculo.latitude < -90 || veiculo.latitude > 90
                    || veiculo.longitude < -180 || veiculo.longitude > 180) {
                continue;
            }

            prefixosAtuais.add(veiculo.prefixo);
            GeoPoint posicao = new GeoPoint(veiculo.latitude, veiculo.longitude);
            pontos.add(posicao);

            Marker marcador = marcadoresPorPrefixo.get(veiculo.prefixo);
            if (marcador == null) {
                marcador = new Marker(binding.mapView);
                marcador.setTitle("Veículo " + veiculo.prefixo);
                marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                Drawable icone = DrawableCompat.wrap(getDrawable(R.drawable.ic_bus)).mutate();
                marcador.setIcon(icone);
                marcadoresPorPrefixo.put(veiculo.prefixo, marcador);
                binding.mapView.getOverlays().add(marcador);
            }
            marcador.setPosition(posicao);
            marcador.setSnippet("Atualização: " + veiculo.horarioAtualizacao
                    + (veiculo.acessivel ? " • Acessível" : ""));
        }

        Iterator<Map.Entry<String, Marker>> it = marcadoresPorPrefixo.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Marker> entry = it.next();
            if (!prefixosAtuais.contains(entry.getKey())) {
                binding.mapView.getOverlays().remove(entry.getValue());
                entry.getValue().onDetach(binding.mapView);
                it.remove();
            }
        }

        if (!cameraAjustada && pontos.size() == 1) {
            binding.mapView.getController().setZoom(15.0);
            binding.mapView.getController().animateTo(pontos.get(0));
            cameraAjustada = true;
        } else if (!cameraAjustada && pontos.size() > 1) {
            binding.mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(pontos), true, 100);
            cameraAjustada = true;
        }
        binding.mapView.invalidate();
    }

    private void carregarPrevisaoDaParada() {
        binding.textStatus.setText("Carregando próximas chegadas…");
        pager.setItems(null);
        repository.previsaoPorParada(codigoParada, new RepositoryCallback<PrevisaoParada>() {
            @Override
            public void onSuccess(PrevisaoParada result) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    binding.textStatus.setText(result.previsoes.isEmpty()
                            ? "Sem previsões em tempo real para este ponto" : "Próximas chegadas • previsão em tempo real");
                    if (result.previsoes.isEmpty()) {
                        binding.recyclerPrevisoes.setVisibility(View.GONE);
                    } else {
                        binding.recyclerPrevisoes.setVisibility(View.VISIBLE);
                        pager.setItems(result.previsoes);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    positionInFlight = false;
                    if (!isFinishing() && !isDestroyed()) binding.textStatus.setText(message);
                });
            }
        });
    }
}
