package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.BloqueioData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface IBloqueioDataRepository
        extends JpaRepository<BloqueioData, Long> {

    boolean existsByBarbeiroIdAndData(
            Long barbeiroId,
            LocalDate data
    );

    long deleteByBarbeiroIdAndData(
            Long barbeiroId,
            LocalDate data
    );
}