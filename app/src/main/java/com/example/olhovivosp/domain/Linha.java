package com.example.olhovivosp.domain;

/** Modelo de domínio legível para uma linha de ônibus (isola a UI dos nomes crípticos da API). */
public class Linha {

    public final int codigoLinha;
    public final String letreiro;
    public final int sentido; // 1 = secundário -> principal, 2 = principal -> secundário
    public final String terminalPrincipal;
    public final String terminalSecundario;
    public final boolean circular;

    public Linha(int codigoLinha, String letreiro, int sentido,
                 String terminalPrincipal, String terminalSecundario, boolean circular) {
        this.codigoLinha = codigoLinha;
        this.letreiro = letreiro;
        this.sentido = sentido;
        this.terminalPrincipal = terminalPrincipal;
        this.terminalSecundario = terminalSecundario;
        this.circular = circular;
    }

    /** Texto amigável "letreiro — terminal principal / terminal secundário" para listas. */
    public String descricaoCompleta() {
        return letreiro + " - " + origemPorSentido() + " / " + destinoPorSentido();
    }

    public String origemPorSentido() {
        return sentido == 1 ? terminalSecundario : terminalPrincipal;
    }

    public String destinoPorSentido() {
        return sentido == 1 ? terminalPrincipal : terminalSecundario;
    }
}
