package com.janioofi.nopontosp.network;

import com.janioofi.nopontosp.network.dto.LinhaDto;
import com.janioofi.nopontosp.network.dto.ParadaDto;
import com.janioofi.nopontosp.network.dto.PosicaoResponseDto;
import com.janioofi.nopontosp.network.dto.PrevisaoLinhaResponseDto;
import com.janioofi.nopontosp.network.dto.PrevisaoParadaResponseDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Espelha exatamente os endpoints documentados publicamente pela SPTrans em
 * sptrans.com.br/desenvolvedores para a API Olho Vivo. Nenhum endpoint aqui foi
 * inventado ou obtido por engenharia reversa.
 */
public interface OlhoVivoApi {

    @POST("Login/Autenticar")
    Call<Boolean> autenticar(@Query("token") String token);

    @GET("Linha/Buscar")
    Call<List<LinhaDto>> buscarLinhas(@Query("termosBusca") String termosBusca);

    @GET("Parada/Buscar")
    Call<List<ParadaDto>> buscarParadas(@Query("termosBusca") String termosBusca);

    @GET("Parada/BuscarParadasPorLinha")
    Call<List<ParadaDto>> buscarParadasPorLinha(@Query("codigoLinha") int codigoLinha);

    @GET("Posicao/Linha")
    Call<PosicaoResponseDto> posicaoPorLinha(@Query("codigoLinha") int codigoLinha);

    @GET("Previsao/Parada")
    Call<PrevisaoParadaResponseDto> previsaoPorParada(@Query("codigoParada") int codigoParada);

    @GET("Previsao/Linha")
    Call<PrevisaoLinhaResponseDto> previsaoPorLinha(@Query("codigoLinha") int codigoLinha);
}
