package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.Agendamento;
import com.guilhermelevi.barbearia.domain.enums.StatusAgendamentoEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IAgendamentoRepository extends JpaRepository<Agendamento,Long> {

    List<Agendamento> findByBarbeiroIdAndInicioBetweenAndStatus(
            Long barbeiroId,
            LocalDateTime inicio,
            LocalDateTime fim,
            StatusAgendamentoEnum status
    );

    @Query("""
            select a from Agendamento a
            join fetch a.servico
            where a.barbeiro.id = :barbeiroId
              and a.inicio < :limite
              and (a.status = :status or a.status is null)
            """)
    List<Agendamento> buscarReservasAntesDe(
            @Param("barbeiroId") Long barbeiroId,
            @Param("limite") LocalDateTime limite,
            @Param("status") StatusAgendamentoEnum status
    );

    @Query("""
            select a from Agendamento a
            join fetch a.servico
            where a.cliente.id = :clienteId
              and a.barbeiro.id = :barbeiroId
              and a.inicio >= :agora
              and (a.status = :status or a.status is null)
            order by a.inicio asc, a.id asc
            """)
    List<Agendamento> buscarProximosDoCliente(
            @Param("clienteId") Long clienteId,
            @Param("barbeiroId") Long barbeiroId,
            @Param("agora") LocalDateTime agora,
            @Param("status") StatusAgendamentoEnum status
    );

    @Query("""
            select a from Agendamento a
            join fetch a.servico
            where a.id = :agendamentoId
              and a.cliente.id = :clienteId
              and a.barbeiro.id = :barbeiroId
            """)
    Optional<Agendamento> buscarDoClienteNaBarbearia(
            @Param("agendamentoId") Long agendamentoId,
            @Param("clienteId") Long clienteId,
            @Param("barbeiroId") Long barbeiroId
    );

    @Query("""
        SELECT a
        FROM Agendamento a
        JOIN FETCH a.cliente
        JOIN FETCH a.servico
        WHERE a.barbeiro.id = :barbeiroId
          AND a.inicio >= :inicioDia
          AND a.inicio < :fimDia
          AND (a.status = :status OR a.status IS NULL)
        ORDER BY a.inicio
        """)
    List<Agendamento> buscarAfetadosPorBloqueio(
            @Param("barbeiroId") Long barbeiroId,
            @Param("inicioDia") LocalDateTime inicioDia,
            @Param("fimDia") LocalDateTime fimDia,
            @Param("status") StatusAgendamentoEnum status
    );


}