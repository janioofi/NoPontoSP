package com.example.olhovivosp.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Resposta de GET /Parada/Buscar, /Parada/BuscarParadasPorLinha e
 * /Parada/BuscarParadasPorCorredor.
 */
public class ParadaDto {

    @SerializedName("cp")
    public int codigoParada;

    @SerializedName("np")
    public String nomeParada;

    @SerializedName("ed")
    public String endereco;

    @SerializedName("py")
    public double latitude;

    @SerializedName("px")
    public double longitude;
}
