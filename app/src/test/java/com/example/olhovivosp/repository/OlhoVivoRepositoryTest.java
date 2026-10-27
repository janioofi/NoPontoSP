package com.example.olhovivosp.repository;

import com.example.olhovivosp.network.dto.LinhaPosicaoDto;
import com.example.olhovivosp.network.dto.PosicaoResponseDto;
import com.example.olhovivosp.network.dto.VeiculoDto;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class OlhoVivoRepositoryTest {

    @Test
    public void mapVeiculosReturnsOnlyRequestedDirection() {
        PosicaoResponseDto response = new PosicaoResponseDto();
        LinhaPosicaoDto ida = linhaComVeiculo(1, "12345");
        LinhaPosicaoDto volta = linhaComVeiculo(2, "67890");
        response.linhas = Arrays.asList(ida, volta);

        assertEquals("12345", OlhoVivoRepository.mapVeiculos(response, 1, null).get(0).prefixo);
        assertEquals(1, OlhoVivoRepository.mapVeiculos(response, 1, null).size());
    }

    @Test
    public void mapVeiculosDoesNotShowOtherDirectionWhenRequestedDirectionIsMissing() {
        PosicaoResponseDto response = new PosicaoResponseDto();
        response.linhas = Collections.singletonList(linhaComVeiculo(2, "67890"));

        assertEquals(0, OlhoVivoRepository.mapVeiculos(response, 1, null).size());
    }

    @Test
    public void mapVeiculosUsesApiDestinationSignWhenItDiffersFromSenseCode() {
        PosicaoResponseDto response = new PosicaoResponseDto();
        LinhaPosicaoDto pirituba = linhaComVeiculo(1, "12345");
        pirituba.letreiroDestino = "Term. Pirituba";
        LinhaPosicaoDto pracaRamos = linhaComVeiculo(2, "67890");
        pracaRamos.letreiroDestino = "Pça. Ramos de Azevedo";
        response.linhas = Arrays.asList(pirituba, pracaRamos);

        assertEquals("12345", OlhoVivoRepository
                .mapVeiculos(response, 2, "Term. Pirituba").get(0).prefixo);
        assertEquals(1, OlhoVivoRepository.mapVeiculos(response, 2, "Term. Pirituba").size());
    }

    @Test
    public void mapVeiculosKeepsFlatResponseWhenDirectionGroupsAreUnavailable() {
        PosicaoResponseDto response = new PosicaoResponseDto();
        VeiculoDto veiculo = new VeiculoDto();
        veiculo.prefixo = "12345";
        response.veiculos = Collections.singletonList(veiculo);

        assertEquals("12345", OlhoVivoRepository.mapVeiculos(response, 1, null).get(0).prefixo);
    }

    private LinhaPosicaoDto linhaComVeiculo(int sentido, String prefixo) {
        LinhaPosicaoDto linha = new LinhaPosicaoDto();
        linha.sentido = sentido;
        linha.letreiroDestino = "";
        VeiculoDto veiculo = new VeiculoDto();
        veiculo.prefixo = prefixo;
        linha.veiculos = Collections.singletonList(veiculo);
        return linha;
    }
}
