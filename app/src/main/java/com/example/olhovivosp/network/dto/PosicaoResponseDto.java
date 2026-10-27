package com.example.olhovivosp.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Corpo completo de GET /Posicao e GET /Posicao/Linha.
 */
public class PosicaoResponseDto {

    @SerializedName("hr")
    public String horarioServidor;

    @SerializedName("vs")
    public List<VeiculoDto> veiculos;

    @SerializedName("l")
    public List<LinhaPosicaoDto> linhas;
}
