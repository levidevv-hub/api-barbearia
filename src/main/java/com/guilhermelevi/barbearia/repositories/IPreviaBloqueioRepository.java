package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.PreviaBloqueio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IPreviaBloqueioRepository
        extends JpaRepository<PreviaBloqueio, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM PreviaBloqueio p
            WHERE p.id = :id
              AND p.barbeiro.id = :barbeiroId
              AND p.numeroAdministrador = :numeroAdministrador
            """)
    Optional<PreviaBloqueio> buscarParaConfirmar(
            @Param("id") UUID id,
            @Param("barbeiroId") Long barbeiroId,
            @Param("numeroAdministrador") String numeroAdministrador
    );
}