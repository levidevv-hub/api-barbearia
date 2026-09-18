package com.guilhermelevi.barbearia.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public record PeriodoAgendamento(LocalDateTime inicio, LocalDateTime fim) {

    public PeriodoAgendamento {
        Objects.requireNonNull(inicio, "O início é obrigatório.");
        Objects.requireNonNull(fim, "O fim é obrigatório.");
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("O fim deve ser posterior ao início.");
        }
    }

    public boolean sobrepoe(PeriodoAgendamento outro) {
        return inicio.isBefore(outro.fim) && fim.isAfter(outro.inicio);
    }
}