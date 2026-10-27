package com.example.olhovivosp.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LinhaTest {

    @Test
    public void destinationMatchesApiDirection() {
        Linha ida = new Linha(1, "123", 1, "Terminal A", "Terminal B", false);
        Linha volta = new Linha(1, "123", 2, "Terminal A", "Terminal B", false);

        assertEquals("Terminal A", ida.destinoPorSentido());
        assertEquals("Terminal B", volta.destinoPorSentido());
    }

    @Test
    public void line8400DirectionTwoLeadsToPirituba() {
        Linha pirituba = new Linha(34033, "8400", 2,
                "PÇA. RAMOS DE AZEVEDO", "TERM. PIRITUBA", false);

        assertEquals("TERM. PIRITUBA", pirituba.destinoPorSentido());
    }
    @Test
    public void completeDescriptionRespectsOriginAndDestination() {
        Linha ida = new Linha(34033, "8400-10", 2,
                "PÇA. RAMOS DE AZEVEDO", "TERMINAL PIRITUBA", false);
        Linha volta = new Linha(1265, "8400-10", 1,
                "PÇA. RAMOS DE AZEVEDO", "TERMINAL PIRITUBA", false);
        assertEquals("8400-10 - PÇA. RAMOS DE AZEVEDO / TERMINAL PIRITUBA", ida.descricaoCompleta());
        assertEquals("8400-10 - TERMINAL PIRITUBA / PÇA. RAMOS DE AZEVEDO", volta.descricaoCompleta());
    }
}
