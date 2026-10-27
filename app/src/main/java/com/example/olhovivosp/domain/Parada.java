package com.example.olhovivosp.domain;

public class Parada {

    public final int codigoParada;
    public final String nome;
    public final String endereco;
    public final double latitude;
    public final double longitude;

    public Parada(int codigoParada, String nome, String endereco, double latitude, double longitude) {
        this.codigoParada = codigoParada;
        this.nome = nome;
        this.endereco = endereco;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
