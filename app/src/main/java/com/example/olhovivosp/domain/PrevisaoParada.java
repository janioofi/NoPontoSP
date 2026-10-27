package com.example.olhovivosp.domain;

import java.util.Collections;
import java.util.List;

/** Resultado agregado de GET /Previsao/Parada: a parada consultada e suas previsões. */
public class PrevisaoParada {

    public final String nomeParada;
    public final List<Previsao> previsoes;

    public PrevisaoParada(String nomeParada, List<Previsao> previsoes) {
        this.nomeParada = nomeParada;
        this.previsoes = previsoes != null ? previsoes : Collections.emptyList();
    }
}
