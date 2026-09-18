package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.ExpedienteSemanal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.Optional;

public interface IExpedienteSemanalRepository
        extends JpaRepository<ExpedienteSemanal, Long> {

    Optional<ExpedienteSemanal> findByBarbeiroIdAndDiaSemana(
            Long barbeiroId,
            DayOfWeek diaSemana
    );
}