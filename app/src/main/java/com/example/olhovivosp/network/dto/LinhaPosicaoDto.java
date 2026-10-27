package com.example.olhovivosp.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Um item da lista "l" de GET /Posicao ou /Posicao/Linha: agrupa os veículos de uma
 * linha/sentido específicos.
 */
public class LinhaPosicaoDto {

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
    public List<VeiculoDto> veiculos;
}
