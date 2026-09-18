package com.guilhermelevi.barbearia.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "mensagens_recebidas",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mensagem_recebida_conta_id",
                columnNames = {"phone_number_id", "mensagem_id_meta"}
        )
)
public class MensagemRecebida {

    public enum Status {
        PENDENTE,
        PROCESSANDO,
        PROCESSADA,
        FALHA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number_id", nullable = false)
    private String phoneNumberId;

    @Column(name = "mensagem_id_meta", nullable = false)
    private String mensagemIdMeta;

    @Column(nullable = false)
    private String remetente;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Instant recebidaEm;

    private Instant processadaEm;

    public MensagemRecebida(
            String phoneNumberId,
            String mensagemIdMeta,
            String remetente,
            String payload
    ) {
        this.phoneNumberId = phoneNumberId;
        this.mensagemIdMeta = mensagemIdMeta;
        this.remetente = remetente;
        this.payload = payload;
        this.status = Status.PENDENTE;
        this.recebidaEm = Instant.now();
    }

    public void iniciarProcessamento() {
        if (status != Status.PENDENTE && status != Status.FALHA) {
            throw new IllegalStateException(
                    "Essa mensagem não está disponível para processamento."
            );
        }

        status = Status.PROCESSANDO;
    }

    public void concluirProcessamento() {
        if (status != Status.PROCESSANDO) {
            throw new IllegalStateException(
                    "A mensagem não está em processamento."
            );
        }

        status = Status.PROCESSADA;
        processadaEm = Instant.now();
    }

    public void registrarFalha() {
        if (status != Status.PROCESSANDO) {
            throw new IllegalStateException(
                    "A mensagem não está em processamento."
            );
        }

        status = Status.FALHA;
    }
}