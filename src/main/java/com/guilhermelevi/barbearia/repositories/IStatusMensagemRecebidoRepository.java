package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.NotificacaoPendente;
import com.guilhermelevi.barbearia.domain.StatusMensagemRecebido;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IStatusMensagemRecebidoRepository
        extends JpaRepository<StatusMensagemRecebido, Long> {

    List<StatusMensagemRecebido>
    findByProcessadoFalseOrderByRecebidoEmAscIdAsc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM StatusMensagemRecebido s
            WHERE s.id = :id
            """)
    Optional<StatusMensagemRecebido> buscarParaProcessar(
            @Param("id") Long id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NotificacaoPendente> findByMensagemIdMeta(
            String mensagemIdMeta
    );

    @Query("""
        SELECT s
        FROM StatusMensagemRecebido s
        WHERE s.processado = false
          AND EXISTS (
              SELECT n.id
              FROM NotificacaoPendente n
              WHERE n.mensagemIdMeta = s.mensagemIdMeta
          )
        ORDER BY s.recebidoEm ASC, s.id ASC
        """)
    List<StatusMensagemRecebido> buscarProntosParaProcessar(
            Pageable pageable
    );
}