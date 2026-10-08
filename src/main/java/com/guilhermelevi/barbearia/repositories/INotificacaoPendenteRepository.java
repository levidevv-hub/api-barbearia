package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.NotificacaoPendente;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface INotificacaoPendenteRepository
        extends JpaRepository<NotificacaoPendente, Long> {

    List<NotificacaoPendente> findByStatusOrderByCriadaEmAscIdAsc(
            NotificacaoPendente.Status status,
            Pageable pageable
    );

    // Filtra a pausa antes de paginar: 20 avisos pausados não podem impedir
    // que outras barbearias recebam seus cancelamentos.
    @Query("""
            SELECT n FROM NotificacaoPendente n
            WHERE n.status = :status
              AND n.agendamento.barbeiro.roboAtivo = true
            ORDER BY n.criadaEm, n.id
            """)
    List<NotificacaoPendente> buscarPendentesDeRobosAtivos(@Param("status") NotificacaoPendente.Status status, Pageable pageable);

    Optional<NotificacaoPendente> findByMensagemIdMeta(
            String mensagemIdMeta
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT n
        FROM NotificacaoPendente n
        WHERE n.id = :id
        """)
    Optional<NotificacaoPendente> buscarParaEnviar(
            @Param("id") Long id
    );
}
