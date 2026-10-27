package com.example.olhovivosp.repository;

import com.example.olhovivosp.BuildConfig;
import com.example.olhovivosp.domain.Linha;
import com.example.olhovivosp.domain.Parada;
import com.example.olhovivosp.domain.Previsao;
import com.example.olhovivosp.domain.PrevisaoParada;
import com.example.olhovivosp.domain.Veiculo;
import com.example.olhovivosp.network.ApiClient;
import com.example.olhovivosp.network.OlhoVivoApi;
import com.example.olhovivosp.network.dto.LinhaDto;
import com.example.olhovivosp.network.dto.LinhaPosicaoDto;
import com.example.olhovivosp.network.dto.LinhaPrevisaoDto;
import com.example.olhovivosp.network.dto.ParadaDto;
import com.example.olhovivosp.network.dto.PontoPrevisaoDto;
import com.example.olhovivosp.network.dto.PosicaoResponseDto;
import com.example.olhovivosp.network.dto.PrevisaoParadaResponseDto;
import com.example.olhovivosp.network.dto.VeiculoDto;
import com.example.olhovivosp.network.dto.VeiculoPrevisaoDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.text.Normalizer;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Único ponto de acesso à API Olho Vivo. Converte DTOs em modelos de domínio e trata
 * reautenticação automática: a sessão (cookie) expira após alguns minutos de
 * inatividade, então qualquer chamada que falhe é tentada novamente uma vez após
 * reautenticar.
 */
public class OlhoVivoRepository {

    private static volatile OlhoVivoRepository instance;

    private final OlhoVivoApi api;
    private final ApiClient apiClient;
    private final String apiToken;
    private volatile boolean authenticated = false;

    private OlhoVivoRepository() {
        apiClient = ApiClient.getInstance();
        api = apiClient.getApi();
        apiToken = BuildConfig.OLHOVIVO_API_TOKEN;
    }

    public static OlhoVivoRepository getInstance() {
        if (instance == null) {
            synchronized (OlhoVivoRepository.class) {
                if (instance == null) {
                    instance = new OlhoVivoRepository();
                }
            }
        }
        return instance;
    }

    public void buscarLinhas(String termosBusca, RepositoryCallback<List<Linha>> callback) {
        executeWithAuth(
                () -> api.buscarLinhas(termosBusca),
                dtos -> {
                    List<Linha> resultado = new ArrayList<>();
                    if (dtos != null) {
                        for (LinhaDto dto : dtos) {
                            resultado.add(mapLinha(dto));
                        }
                    }
                    return resultado;
                },
                callback,
                false
        );
    }

    public void buscarParadas(String termosBusca, RepositoryCallback<List<Parada>> callback) {
        executeWithAuth(
                () -> api.buscarParadas(termosBusca),
                dtos -> mapParadas(dtos),
                callback,
                false
        );
    }

    public void buscarParadasPorLinha(int codigoLinha, RepositoryCallback<List<Parada>> callback) {
        executeWithAuth(
                () -> api.buscarParadasPorLinha(codigoLinha),
                dtos -> mapParadas(dtos),
                callback,
                false
        );
    }

    /** Posição em tempo real dos veículos da linha no sentido/destino informado. */
    public void posicaoPorLinha(int codigoLinha, int sentido, String destino,
                                RepositoryCallback<List<Veiculo>> callback) {
        executeWithAuth(
                () -> api.posicaoPorLinha(codigoLinha),
                response -> mapVeiculos(response, sentido, destino),
                callback,
                false
        );
    }

    /** Previsão de chegada de todas as linhas que atendem a uma parada específica. */
    public void previsaoPorParada(int codigoParada, RepositoryCallback<PrevisaoParada> callback) {
        executeWithAuth(
                () -> api.previsaoPorParada(codigoParada),
                this::mapPrevisaoParada,
                callback,
                false
        );
    }

    // ---- Mapeamento DTO -> domínio ----

    private Linha mapLinha(LinhaDto dto) {
        return new Linha(dto.codigoLinha, dto.letreiro.contains("-") ? dto.letreiro : dto.letreiro + "-" + String.format(java.util.Locale.ROOT, "%02d", dto.tipoLinha), dto.sentido,
                dto.terminalPrincipal, dto.terminalSecundario, dto.circular);
    }

    private List<Parada> mapParadas(List<ParadaDto> dtos) {
        List<Parada> resultado = new ArrayList<>();
        if (dtos != null) {
            for (ParadaDto dto : dtos) {
                resultado.add(new Parada(dto.codigoParada, dto.nomeParada, dto.endereco, dto.latitude, dto.longitude));
            }
        }
        return resultado;
    }

    static List<Veiculo> mapVeiculos(PosicaoResponseDto response, int sentido, String destino) {
        List<Veiculo> resultado = new ArrayList<>();
        if (response != null && response.linhas != null && !response.linhas.isEmpty()) {
            String destinoNormalizado = normalizarDestino(destino);
            boolean respostaTemDestinos = response.linhas.stream()
                    .anyMatch(linha -> !normalizarDestino(linha.letreiroDestino).isEmpty());
            for (LinhaPosicaoDto linha : response.linhas) {
                if (!destinoNormalizado.isEmpty() && respostaTemDestinos) {
                    if (!destinoNormalizado.equals(normalizarDestino(linha.letreiroDestino))) continue;
                } else if (sentido != -1 && linha.sentido != sentido) {
                    continue;
                }
                if (linha.veiculos == null) continue;
                for (VeiculoDto v : linha.veiculos) {
                    resultado.add(new Veiculo(v.prefixo, v.acessivel, v.latitude, v.longitude, v.horarioAtualizacao));
                }
            }
        } else if (response != null && response.veiculos != null) {
            for (VeiculoDto veiculo : response.veiculos) {
                resultado.add(new Veiculo(veiculo.prefixo, veiculo.acessivel, veiculo.latitude,
                        veiculo.longitude, veiculo.horarioAtualizacao));
            }
        }
        return resultado;
    }

    private static String normalizarDestino(String destino) {
        if (destino == null) return "";
        return Normalizer.normalize(destino, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }

    private PrevisaoParada mapPrevisaoParada(PrevisaoParadaResponseDto response) {
        if (response == null || response.ponto == null) {
            return new PrevisaoParada("", Collections.emptyList());
        }
        PontoPrevisaoDto ponto = response.ponto;
        List<Previsao> previsoes = new ArrayList<>();
        if (ponto.linhas != null) {
            for (LinhaPrevisaoDto linha : ponto.linhas) {
                if (linha.veiculos == null) continue;
                for (VeiculoPrevisaoDto v : linha.veiculos) {
                    previsoes.add(new Previsao(linha.letreiro + " • " + linha.letreiroDestino, v.prefixo, v.horarioPrevisto, v.acessivel));
                }
            }
        }
        return new PrevisaoParada(ponto.nomeParada, previsoes);
    }

    // ---- Infraestrutura de autenticação/retry ----

    private interface CallFactory<T> {
        Call<T> create();
    }

    private interface Mapper<D, M> {
        M map(D dto);
    }

    private <D, M> void executeWithAuth(CallFactory<D> callFactory, Mapper<D, M> mapper,
                                         RepositoryCallback<M> callback, boolean isRetry) {
        if (apiToken == null || apiToken.trim().isEmpty() || apiToken.contains("coloque_seu_token")) {
            callback.onError("Token da API Olho Vivo não configurado. Preencha OLHOVIVO_API_TOKEN em local.properties.");
            return;
        }

        if (!authenticated) {
            autenticar(
                    () -> executeWithAuth(callFactory, mapper, callback, isRetry),
                    callback::onError
            );
            return;
        }

        callFactory.create().enqueue(new Callback<D>() {
            @Override
            public void onResponse(Call<D> call, Response<D> response) {
                if (response.isSuccessful() && response.body() != null) {
                    java.util.concurrent.ForkJoinPool.commonPool().execute(() -> {
                        try { callback.onSuccess(mapper.map(response.body())); }
                        catch (Exception error) { callback.onError("Não foi possível interpretar a resposta da SPTrans."); }
                    });
                } else if (!isRetry && (response.code() == 401 || response.code() == 403)) {
                    // Provável expiração de sessão: reautentica e tenta mais uma vez.
                    authenticated = false;
                    apiClient.resetSession();
                    executeWithAuth(callFactory, mapper, callback, true);
                } else {
                    callback.onError("Falha na comunicação com a API Olho Vivo (HTTP " + response.code() + ").");
                }
            }

            @Override
            public void onFailure(Call<D> call, Throwable t) {
                callback.onError("Falha de comunicação com a API Olho Vivo: " + t.getMessage());
            }
        });
    }

    private void autenticar(Runnable onSuccess, java.util.function.Consumer<String> onError) {
        api.autenticar(apiToken).enqueue(new Callback<Boolean>() {
            @Override
            public void onResponse(Call<Boolean> call, Response<Boolean> response) {
                if (response.isSuccessful() && Boolean.TRUE.equals(response.body())) {
                    authenticated = true;
                    onSuccess.run();
                } else if (!response.isSuccessful() && response.code() >= 500) {
                    onError.accept("A API Olho Vivo retornou erro HTTP " + response.code()
                            + " ao autenticar. Tente novamente mais tarde; se persistir, confirme se o token está ativo.");
                } else {
                    onError.accept("Token inválido ou não autorizado pela API Olho Vivo (HTTP "
                            + response.code() + "). Verifique seu cadastro em sptrans.com.br/desenvolvedores.");
                }
            }

            @Override
            public void onFailure(Call<Boolean> call, Throwable t) {
                onError.accept("Falha ao autenticar com a API Olho Vivo: " + t.getMessage());
            }
        });
    }
}
