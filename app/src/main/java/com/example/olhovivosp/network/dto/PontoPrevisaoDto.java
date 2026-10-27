package com.example.olhovivosp.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Dados do ponto de parada ("p") dentro da resposta de GET /Previsao/Parada, e cada
 * item da lista "ps" de GET /Previsao e GET /Previsao/Linha.
 */
public class PontoPrevisaoDto {

    @SerializedName("cp")
    public int codigoParada;

    @SerializedName("np")
    public String nomeParada;

    @SerializedName("py")
    public double latitude;

    @SerializedName("px")
    public double longitude;

    @SerializedName("l")
    public List<LinhaPrevisaoDto> linhas;
}
