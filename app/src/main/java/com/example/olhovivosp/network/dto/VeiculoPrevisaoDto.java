package com.example.olhovivosp.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Um veículo previsto dentro da lista "vs" de uma linha em /Previsao, /Previsao/Linha
 * ou /Previsao/Parada. Difere de VeiculoDto por trazer o horário estimado de chegada ("t").
 */
public class VeiculoPrevisaoDto {

    @SerializedName("p")
    public String prefixo;

    @SerializedName("t")
    public String horarioPrevisto; // "HH:mm"

    @SerializedName("a")
    public boolean acessivel;

    @SerializedName("ta")
    public String horarioAtualizacao;

    @SerializedName("py")
    public double latitude;

    @SerializedName("px")
    public double longitude;
}
