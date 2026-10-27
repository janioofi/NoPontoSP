package com.example.olhovivosp.network.dto;

import com.google.gson.Gson;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PosicaoResponseDtoTest {

    @Test
    public void parsesVehiclesFromLinePositionResponse() {
        String json = "{\"hr\":\"2026-10-26T22:00:00\",\"vs\":["
                + "{\"p\":\"12345\",\"a\":true,\"ta\":\"2026-10-26T21:59:00\","
                + "\"py\":-23.55,\"px\":-46.63}]}";

        PosicaoResponseDto response = new Gson().fromJson(json, PosicaoResponseDto.class);

        assertNotNull(response.veiculos);
        assertEquals(1, response.veiculos.size());
        assertEquals("12345", response.veiculos.get(0).prefixo);
        assertEquals(-23.55, response.veiculos.get(0).latitude, 0.000001);
        assertEquals(-46.63, response.veiculos.get(0).longitude, 0.000001);
    }

    @Test
    public void parsesGroupedVehicleResponseAsFallback() {
        String json = "{\"hr\":\"2026-10-26T22:00:00\",\"l\":["
                + "{\"cl\":123,\"sl\":1,\"vs\":[{\"p\":\"67890\",\"py\":-23.5,\"px\":-46.6}]}]}";

        PosicaoResponseDto response = new Gson().fromJson(json, PosicaoResponseDto.class);

        assertNotNull(response.linhas);
        assertEquals(1, response.linhas.get(0).veiculos.size());
    }
}
