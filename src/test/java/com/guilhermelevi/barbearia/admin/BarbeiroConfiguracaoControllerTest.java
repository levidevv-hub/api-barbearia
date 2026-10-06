package com.guilhermelevi.barbearia.admin;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.guilhermelevi.barbearia.admin.BarbeiroConfiguracaoController.*;

class BarbeiroConfiguracaoControllerTest {
    private List<Dia> semana() {
        return new ArrayList<>(Arrays.stream(DayOfWeek.values())
                .map(d -> new Dia(d, false, List.of())).toList());
    }
    private Periodo periodo(String inicio, String fim) {
        return new Periodo(LocalTime.parse(inicio), LocalTime.parse(fim));
    }
    @Test void aceitaAlmocoEDomingoFechado() {
        var dias = semana();
        dias.set(0, new Dia(DayOfWeek.MONDAY, true, List.of(periodo("08:00","12:00"), periodo("13:30","19:00"))));
        assertDoesNotThrow(() -> validar(new Configuracao(dias, "Rua Teste", -4.94, -37.97)));
    }
    @Test void rejeitaSobreposicao() {
        var dias = semana();
        dias.set(0, new Dia(DayOfWeek.MONDAY, true, List.of(periodo("08:00","12:00"), periodo("11:30","19:00"))));
        assertThrows(IllegalArgumentException.class, () -> validar(new Configuracao(dias, null, null, null)));
    }
    @Test void rejeitaDiaDuplicado() {
        var dias = semana();dias.set(1, dias.get(0));
        assertThrows(IllegalArgumentException.class, () -> validar(new Configuracao(dias, null, null, null)));
    }
    @Test void rejeitaAbertoSemPeriodos() {
        var dias = semana();dias.set(0, new Dia(DayOfWeek.MONDAY, true, List.of()));
        assertThrows(IllegalArgumentException.class, () -> validar(new Configuracao(dias, null, null, null)));
    }
    @Test void rejeitaCoordenadasIncompletasEForaDosLimites() {
        assertThrows(IllegalArgumentException.class, () -> validar(new Configuracao(semana(), null, -4.94, null)));
        assertThrows(IllegalArgumentException.class, () -> validar(new Configuracao(semana(), null, 91.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> validar(new Configuracao(semana(), null, Double.NaN, 0.0)));
    }
    @Test void aceitaTodosFechadosECoordenadasRemovidas() {
        assertDoesNotThrow(() -> validar(new Configuracao(semana(), "", null, null)));
    }
    @Test void rejeitaViradaDeDia() {
        var dias = semana();dias.set(0, new Dia(DayOfWeek.MONDAY,true,List.of(periodo("20:00","02:00"))));
        assertThrows(IllegalArgumentException.class, () -> validar(new Configuracao(dias,null,null,null)));
    }
}
