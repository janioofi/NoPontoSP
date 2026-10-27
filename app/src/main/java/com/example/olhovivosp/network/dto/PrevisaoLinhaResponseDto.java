package com.example.olhovivosp.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Corpo de GET /Previsao/Linha?codigoLinha= : lista de pontos de parada atendidos
 * pela linha, cada um com a previsão de chegada.
 */
public class PrevisaoLinhaResponseDto {

    @SerializedName("hr")
    public String horarioServidor;

    @SerializedName("ps")
    public List<PontoPrevisaoDto> pontos;
}
