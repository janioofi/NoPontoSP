package com.example.olhovivosp.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Linha dentro da lista "l" de um ponto de parada em /Previsao, /Previsao/Linha
 * ou /Previsao/Parada.
 */
public class LinhaPrevisaoDto {

    @SerializedName("c")
    public String letreiro;

    @SerializedName("cl")
    public int codigoLinha;

    @SerializedName("sl")
    public int sentido;

    @SerializedName("lt0")
    public String letreiroDestino;

    @SerializedName("lt1")
    public String letreiroOrigem;

    @SerializedName("qv")
    public int quantidadeVeiculos;

    @SerializedName("vs")
    public List<VeiculoPrevisaoDto> veiculos;
}
