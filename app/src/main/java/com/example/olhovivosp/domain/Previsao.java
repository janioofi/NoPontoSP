package com.example.olhovivosp.domain;

/** Uma previsão individual de chegada: um veículo de uma linha, em um horário estimado. */
public class Previsao {

    public final String letreiroLinha;
    public final String prefixoVeiculo;
    public final String horarioPrevisto; // "HH:mm", como fornecido pela API
    public final boolean acessivel;

    public Previsao(String letreiroLinha, String prefixoVeiculo, String horarioPrevisto, boolean acessivel) {
        this.letreiroLinha = letreiroLinha;
        this.prefixoVeiculo = prefixoVeiculo;
        this.horarioPrevisto = horarioPrevisto;
        this.acessivel = acessivel;
    }
}
