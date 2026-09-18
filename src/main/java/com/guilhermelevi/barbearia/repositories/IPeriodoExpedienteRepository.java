package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.PeriodoExpediente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IPeriodoExpedienteRepository
        extends JpaRepository<PeriodoExpediente, Long> {

    List<PeriodoExpediente> findByExpedienteSemanalIdOrderByInicioAsc(
            Long expedienteSemanalId
    );
}