package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.MensagemRecebida;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface IMensagemRecebidaRepository
        extends JpaRepository<MensagemRecebida, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT m
            FROM MensagemRecebida m
            WHERE m.phoneNumberId = :phoneNumberId
              AND m.mensagemIdMeta = :mensagemIdMeta
            """)
    Optional<MensagemRecebida> buscarParaProcessar(
            @Param("phoneNumberId") String phoneNumberId,
            @Param("mensagemIdMeta") String mensagemIdMeta
    );

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO mensagens_recebidas (
            phone_number_id,
            mensagem_id_meta,
            remetente,
            payload,
            status,
            recebida_em
        )
        VALUES (
            :phoneNumberId,
            :mensagemIdMeta,
            :remetente,
            :payload,
            'PENDENTE',
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (phone_number_id, mensagem_id_meta)
        DO NOTHING
        """, nativeQuery = true)
    int registrarSeNova(
            @Param("phoneNumberId") String phoneNumberId,
            @Param("mensagemIdMeta") String mensagemIdMeta,
            @Param("remetente") String remetente,
            @Param("payload") String payload
    );
}