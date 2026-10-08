package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.BloqueioData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface IBloqueioDataRepository
        extends JpaRepository<BloqueioData, Long> {

    Optional<BloqueioData> findByBarbeiroIdAndData(Long barbeiroId, LocalDate data);

    boolean existsByBarbeiroIdAndData(
            Long barbeiroId,
            LocalDate data
    );

    long deleteByBarbeiroIdAndData(
            Long barbeiroId,
            LocalDate data
    );
}
