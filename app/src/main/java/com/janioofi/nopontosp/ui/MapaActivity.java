package com.janioofi.nopontosp.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
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

import com.janioofi.nopontosp.BuildConfig;
import com.janioofi.nopontosp.R;
import com.janioofi.nopontosp.databinding.ActivityMapaBinding;
import com.janioofi.nopontosp.domain.Parada;
import com.janioofi.nopontosp.domain.PrevisaoParada;
import com.janioofi.nopontosp.domain.Veiculo;
import com.janioofi.nopontosp.repository.OlhoVivoRepository;
import com.janioofi.nopontosp.repository.RepositoryCallback;
import com.janioofi.nopontosp.service.TransitAlertService;
import com.janioofi.nopontosp.ui.adapters.PrevisaoAdapter;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;
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
    private static final int REQUEST_PERMISSAO_NOTIFICACOES = 1002;

    private ActivityMapaBinding binding;
    private PrevisaoAdapter previsaoAdapter;
    private PageController<com.janioofi.nopontosp.domain.Previsao> pager;
    private MyLocationNewOverlay localizacaoOverlay;
    private Polyline trajetoOverlay;
    private OlhoVivoRepository repository;
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Marker> marcadoresPorPrefixo = new HashMap<>();
    private final List<Marker> marcadoresParadasRota = new ArrayList<>();

    private int codigoLinha;
    private int sentido = -1;
    private int codigoParada = -1;
    private String destinoSentido;
    private String letreiro;
    private String nomeParada;
    private double paradaLat = Double.NaN;
    private double paradaLng = Double.NaN;
    private boolean positionInFlight;
    private boolean cameraAjustada;
    private boolean localizacaoAtivada;
    private boolean painelExpandido;
    private boolean opcoesMapaAbertas;
    private String prefixoVeiculoSelecionado;

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
                "NoPontoSP/" + BuildConfig.VERSION_NAME
                        + " (Android; package=" + getPackageName() + ")");
        if (BuildConfig.DEBUG) {
            Configuration.getInstance().setDebugMapTileDownloader(true);
        }
        binding = ActivityMapaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = OlhoVivoRepository.getInstance(this);

        codigoLinha = getIntent().getIntExtra(EXTRA_CODIGO_LINHA, -1);
        sentido = getIntent().getIntExtra(EXTRA_SENTIDO, -1);
        codigoParada = getIntent().getIntExtra(EXTRA_CODIGO_PARADA, -1);
        letreiro = getIntent().getStringExtra(EXTRA_LETREIRO);
        destinoSentido = getIntent().getStringExtra(EXTRA_DESTINO_SENTIDO);
        nomeParada = getIntent().getStringExtra(EXTRA_NOME_PARADA);
        binding.textInfoTrajeto.setVisibility(codigoLinha == -1 ? View.GONE : View.VISIBLE);

        if (letreiro != null && destinoSentido != null && !destinoSentido.trim().isEmpty()) {
            binding.toolbar.setTitle(getString(R.string.format_mapa_linha_destino, letreiro, destinoSentido));
        } else {
            binding.toolbar.setTitle(letreiro != null ? letreiro : getString(R.string.title_mapa));
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        binding.mapView.setTileSource(TileSourceFactory.MAPNIK);
        binding.mapView.setMultiTouchControls(true);
        binding.mapView.getOverlayManager().getTilesOverlay().setColorFilter(null);
        binding.textMapAttribution.setText(R.string.map_attribution_light);
        binding.btnProximityAlert.setVisibility(
                codigoLinha >= 0 && codigoParada >= 0 ? View.VISIBLE : View.GONE);
        binding.btnProximityAlert.setOnClickListener(v -> alternarAlertaProximidade());
        binding.switchMostrarPontos.setVisibility(codigoLinha == -1 ? View.GONE : View.VISIBLE);
        binding.switchMostrarPontos.setOnCheckedChangeListener((button, checked) ->
                atualizarVisibilidadeParadasRota(checked));
        binding.btnExpandPanel.setOnClickListener(v -> {
            painelExpandido = !painelExpandido;
            binding.panelExpandedContent.setVisibility(painelExpandido ? View.VISIBLE : View.GONE);
            binding.btnExpandPanel.setText(painelExpandido
                    ? R.string.map_hide_details : R.string.map_show_details);
        });
        binding.btnMapOptions.setOnClickListener(v -> {
            opcoesMapaAbertas = !opcoesMapaAbertas;
            binding.panelMapOptions.setVisibility(opcoesMapaAbertas ? View.VISIBLE : View.GONE);
            binding.btnMapOptions.setText(opcoesMapaAbertas
                    ? R.string.map_hide_options : R.string.map_options);
        });
        binding.btnMapOptions.setVisibility(codigoLinha == -1 ? View.GONE : View.VISIBLE);
        binding.textMapAttribution.setText(R.string.map_attribution_light);
        localizacaoOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(this), binding.mapView);
        android.graphics.Bitmap location = locationBitmap();
        localizacaoOverlay.setPersonIcon(location);
        localizacaoOverlay.setDirectionIcon(location);
        localizacaoOverlay.setPersonAnchor(0.5f, 0.5f);
        localizacaoOverlay.setDirectionAnchor(0.5f, 0.5f);
        binding.mapView.getOverlays().add(localizacaoOverlay);
        binding.btnMinhaLocalizacao.setOnClickListener(v -> centralizarNaMinhaLocalizacao());

        paradaLat = getIntent().getDoubleExtra(EXTRA_PARADA_LAT, Double.NaN);
        paradaLng = getIntent().getDoubleExtra(EXTRA_PARADA_LNG, Double.NaN);
        binding.btnProximityAlert.setVisibility(
                codigoLinha >= 0 && codigoParada >= 0
                        && Double.isFinite(paradaLat) && Double.isFinite(paradaLng)
                        ? View.VISIBLE : View.GONE);
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
            DrawableCompat.setTint(icone, ContextCompat.getColor(this, R.color.warning));
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
        binding.btnProximityAlert.setText(TransitAlertService.isActive()
                ? R.string.alert_cancel : R.string.alert_nearby);
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
            List<GeoPoint> points = java.util.Collections.emptyList();
            List<Parada> stops = java.util.Collections.emptyList();
            String stopsError = null;
            try {
                points = com.janioofi.nopontosp.repository.GtfsGeometry.load(getAssets(), route, sentido, destinoSentido);
            } catch (Exception error) {
                android.util.Log.e("MapaActivity", "Não foi possível carregar a geometria GTFS.", error);
            }
            try {
                stops = com.janioofi.nopontosp.repository.GtfsGeometry.loadStops(
                        getAssets(), route, sentido, destinoSentido);
            } catch (Exception error) {
                stopsError = error.getLocalizedMessage();
                android.util.Log.e("MapaActivity", "Não foi possível carregar as paradas GTFS.", error);
            }
            final List<GeoPoint> geometry = points;
            final List<Parada> routeStops = stops;
            final String routeStopsError = stopsError;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (geometry.size() < 2) {
                    binding.textInfoTrajeto.setText("Trajeto GTFS indisponível para este sentido");
                    return;
                }
                trajetoOverlay = new Polyline(binding.mapView);
                trajetoOverlay.setPoints(geometry);
                trajetoOverlay.setColor(ContextCompat.getColor(this, R.color.primary_teal));
                trajetoOverlay.setWidth(8f);
                binding.mapView.getOverlays().add(1, trajetoOverlay);
                binding.textInfoTrajeto.setText("Trajeto GTFS • sentido selecionado");
                if (!cameraAjustada) {
                    binding.mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(geometry), true, 80);
                    cameraAjustada = true;
                }
                carregarParadasDaRota(routeStops, routeStopsError, geometry);
                binding.mapView.invalidate();
            });
        });
    }

    private void carregarParadasDaRota(List<Parada> paradas, String error,
                                       List<GeoPoint> geometry) {
        List<Parada> validStops = new ArrayList<>();
        for (Parada parada : paradas) {
            if (!Double.isFinite(parada.latitude) || !Double.isFinite(parada.longitude)
                    || parada.latitude < -90 || parada.latitude > 90
                    || parada.longitude < -180 || parada.longitude > 180) {
                continue;
            }
            validStops.add(parada);
        }

        int count = validStops.size();
        int firstIntermediate = validStops.size() >= 2 ? 1 : 0;
        int lastIntermediate = validStops.size() >= 2 ? validStops.size() - 1 : validStops.size();
        for (int i = firstIntermediate; i < lastIntermediate; i++) {
            Parada parada = validStops.get(i);
            Marker marker = new Marker(binding.mapView);
            marker.setPosition(new GeoPoint(parada.latitude, parada.longitude));
            marker.setTitle("Ponto de ônibus");
            marker.setSnippet(parada.nome);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(getDrawable(R.drawable.ic_bus_stop));
            marcadoresParadasRota.add(marker);
            if (binding.switchMostrarPontos.isChecked()) {
                binding.mapView.getOverlays().add(marker);
            }
        }

        if (validStops.size() >= 2) {
            addRouteEndpoint(validStops.get(0), R.string.legend_route_start, R.drawable.ic_route_start);
            addRouteEndpoint(validStops.get(validStops.size() - 1), R.string.legend_route_end, R.drawable.ic_route_end);
        } else if (geometry.size() >= 2) {
            addRouteEndpoint(geometry.get(0), R.string.legend_route_start, R.drawable.ic_route_start);
            addRouteEndpoint(geometry.get(geometry.size() - 1), R.string.legend_route_end, R.drawable.ic_route_end);
        }
        binding.textInfoTrajeto.setText(error != null
                ? "Trajeto GTFS • não foi possível ler os pontos"
                : count == 0
                ? "Trajeto GTFS • pontos de parada indisponíveis"
                : "Trajeto GTFS • " + count + " pontos de parada");
    }

    private void atualizarVisibilidadeParadasRota(boolean visible) {
        List<org.osmdroid.views.overlay.Overlay> overlays = binding.mapView.getOverlays();
        for (Marker marker : marcadoresParadasRota) {
            if (visible && !overlays.contains(marker)) {
                overlays.add(marker);
            } else if (!visible) {
                overlays.remove(marker);
            }
        }
        binding.mapView.invalidate();
    }

    private void addRouteEndpoint(Parada parada, int titleRes, int iconRes) {
        addRouteEndpoint(new GeoPoint(parada.latitude, parada.longitude), titleRes, iconRes);
    }

    private void addRouteEndpoint(GeoPoint position, int titleRes, int iconRes) {
        Marker marker = new Marker(binding.mapView);
        marker.setPosition(position);
        marker.setTitle(getString(titleRes));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setIcon(getDrawable(iconRes));
        binding.mapView.getOverlays().add(marker);
    }

    private android.graphics.Bitmap locationBitmap() {
        int size = (int) (32 * getResources().getDisplayMetrics().density);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        float center = size / 2f;
        paint.setColor(ContextCompat.getColor(this, R.color.location_halo));
        canvas.drawCircle(center, center, size * .48f, paint);
        paint.setColor(ContextCompat.getColor(this, R.color.surface_white));
        canvas.drawCircle(center, center, size * .29f, paint);
        paint.setColor(ContextCompat.getColor(this, R.color.marker_veiculo));
        canvas.drawCircle(center, center, size * .21f, paint);
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
        } else if (requestCode == REQUEST_PERMISSAO_NOTIFICACOES) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarAlertaProximidade();
            } else {
                Toast.makeText(this, R.string.alert_notification_permission_required,
                        Toast.LENGTH_LONG).show();
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
                marcador.setIcon(getDrawable(R.drawable.ic_bus));
                marcadoresPorPrefixo.put(veiculo.prefixo, marcador);
                binding.mapView.getOverlays().add(marcador);
            }
            Veiculo veiculoSelecionado = veiculo;
            marcador.setOnMarkerClickListener((clicked, map) -> {
                prefixoVeiculoSelecionado = veiculoSelecionado.prefixo;
                atualizarIconesVeiculos();
                map.invalidate();
                mostrarDetalhesVeiculo(veiculoSelecionado);
                return true;
            });
            marcador.setIcon(getDrawable(java.util.Objects.equals(veiculo.prefixo, prefixoVeiculoSelecionado)
                    ? R.drawable.ic_bus_selected : R.drawable.ic_bus));
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
        if (prefixoVeiculoSelecionado != null
                && !prefixosAtuais.contains(prefixoVeiculoSelecionado)) {
            prefixoVeiculoSelecionado = null;
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

    private void atualizarIconesVeiculos() {
        for (Map.Entry<String, Marker> entry : marcadoresPorPrefixo.entrySet()) {
            entry.getValue().setIcon(getDrawable(java.util.Objects.equals(entry.getKey(), prefixoVeiculoSelecionado)
                    ? R.drawable.ic_bus_selected : R.drawable.ic_bus));
        }
    }

    private void mostrarDetalhesVeiculo(Veiculo veiculo) {
        String linha = letreiro != null ? letreiro : Integer.toString(codigoLinha);
        String destino = destinoSentido == null || destinoSentido.trim().isEmpty()
                ? getString(R.string.label_destino_indisponivel) : destinoSentido;
        String mensagem = getString(R.string.vehicle_details_line, linha) + "\n"
                + getString(R.string.vehicle_details_destination, destino) + "\n"
                + getString(R.string.vehicle_details_prefix, veiculo.prefixo) + "\n"
                + getString(R.string.vehicle_details_accessible,
                getString(veiculo.acessivel ? R.string.yes : R.string.no)) + "\n"
                + getString(R.string.vehicle_details_updated, veiculo.horarioAtualizacao);
        new AlertDialog.Builder(this)
                .setTitle(R.string.vehicle_details_title)
                .setMessage(mensagem)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void alternarAlertaProximidade() {
        if (TransitAlertService.isActive()) {
            stopService(new android.content.Intent(this, TransitAlertService.class)
                    .setAction(TransitAlertService.ACTION_STOP));
            binding.btnProximityAlert.setText(R.string.alert_nearby);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_PERMISSAO_NOTIFICACOES);
            return;
        }
        iniciarAlertaProximidade();
    }

    private void iniciarAlertaProximidade() {
        if (codigoLinha < 0 || codigoParada < 0 || !Double.isFinite(paradaLat)
                || !Double.isFinite(paradaLng)) {
            Toast.makeText(this, R.string.alert_needs_stop, Toast.LENGTH_LONG).show();
            return;
        }
        android.content.Intent intent = new android.content.Intent(this, TransitAlertService.class)
                .setAction(TransitAlertService.ACTION_START)
                .putExtra(TransitAlertService.EXTRA_LINE_CODE, codigoLinha)
                .putExtra(TransitAlertService.EXTRA_DIRECTION, sentido)
                .putExtra(TransitAlertService.EXTRA_DESTINATION, destinoSentido)
                .putExtra(TransitAlertService.EXTRA_LINE_LABEL,
                        letreiro != null ? letreiro : Integer.toString(codigoLinha))
                .putExtra(TransitAlertService.EXTRA_STOP_CODE, codigoParada)
                .putExtra(TransitAlertService.EXTRA_STOP_NAME,
                        nomeParada != null ? nomeParada : Integer.toString(codigoParada))
                .putExtra(TransitAlertService.EXTRA_STOP_LAT, paradaLat)
                .putExtra(TransitAlertService.EXTRA_STOP_LON, paradaLng);
        ContextCompat.startForegroundService(this, intent);
        binding.btnProximityAlert.setText(R.string.alert_cancel);
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
