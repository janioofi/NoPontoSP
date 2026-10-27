package com.example.olhovivosp.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Resposta de GET /Linha/Buscar e GET /Linha/BuscarLinhaSentido.
 * Nomes de campo seguem exatamente o JSON retornado pela API Olho Vivo (abreviados em
 * português pela própria SPTrans) — não renomear sem atualizar o @SerializedName.
 */
public class LinhaDto {

    @SerializedName("cl")
    public int codigoLinha;

    @SerializedName("lc")
    public boolean circular;

    @SerializedName("lt")
    public String letreiro;

    @SerializedName("tl")
    public int tipoLinha;

    @SerializedName("sl")
    public int sentido; // 1 = terminal secundário -> principal, 2 = principal -> secundário

    @SerializedName("tp")
    public String terminalPrincipal;

    @SerializedName("ts")
    public String terminalSecundario;
}
