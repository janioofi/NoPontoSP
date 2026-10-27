package com.example.olhovivosp.domain;

/** Posição em tempo real de um veículo, já filtrada da linha/sentido consultados. */
public class Veiculo {

    public final String prefixo;
    public final boolean acessivel;
    public final double latitude;
    public final double longitude;
    public final String horarioAtualizacao;

    public Veiculo(String prefixo, boolean acessivel, double latitude, double longitude, String horarioAtualizacao) {
        this.prefixo = prefixo;
        this.acessivel = acessivel;
        this.latitude = latitude;
        this.longitude = longitude;
        this.horarioAtualizacao = horarioAtualizacao;
    }
}
