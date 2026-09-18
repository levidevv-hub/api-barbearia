package com.guilhermelevi.barbearia.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "status_mensagens_recebidos",
        indexes = @Index(
                name = "idx_status_recebido_processamento",
                columnList = "processado, recebido_em"
        )
)
public class StatusMensagemRecebido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mensagem_id_meta", nullable = false)
    private String mensagemIdMeta;

    @Column(name = "phone_number_id", nullable = false)
    private String phoneNumberId;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "text")
    private String erro;

    @Column(name = "recebido_em", nullable = false)
    private LocalDateTime recebidoEm;

    @Column(nullable = false)
    private boolean processado;

    public StatusMensagemRecebido(
            String mensagemIdMeta,
            String phoneNumberId,
            String status,
            String erro
    ) {
        if (mensagemIdMeta == null || mensagemIdMeta.isBlank()
                || phoneNumberId == null || phoneNumberId.isBlank()
                || status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                    "Informe o ID da mensagem, o número da API e o status."
            );
        }

        this.mensagemIdMeta = mensagemIdMeta;
        this.phoneNumberId = phoneNumberId;
        this.status = status;
        this.erro = erro;
        this.recebidoEm = LocalDateTime.now();
        this.processado = false;
    }

    public void marcarProcessado() {
        this.processado = true;
    }
}