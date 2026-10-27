package com.example.olhovivosp.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Corpo de GET /Previsao/Parada?codigoParada= e de GET /Previsao?codigoParada=&codigoLinha=
 * (um único ponto de parada com as linhas que o atendem).
 */
public class PrevisaoParadaResponseDto {

    @SerializedName("hr")
    public String horarioServidor;

    @SerializedName("p")
    public PontoPrevisaoDto ponto;
}
