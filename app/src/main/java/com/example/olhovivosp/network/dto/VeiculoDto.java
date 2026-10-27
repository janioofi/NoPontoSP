package com.example.olhovivosp.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Um veículo dentro da lista "vs" de GET /Posicao ou /Posicao/Linha.
 */
public class VeiculoDto {

    @SerializedName("p")
    public String prefixo;

    @SerializedName("a")
    public boolean acessivel;

    @SerializedName("ta")
    public String horarioAtualizacao; // timestamp ISO-8601 da última atualização de GPS

    @SerializedName("py")
    public double latitude;

    @SerializedName("px")
    public double longitude;
}
