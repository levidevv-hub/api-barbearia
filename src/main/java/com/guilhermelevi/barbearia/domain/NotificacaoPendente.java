package com.guilhermelevi.barbearia.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "notificacoes_pendentes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notificacao_agendamento_tipo",
                columnNames = {"agendamento_id", "tipo"}
        )
)
public class NotificacaoPendente {

    public enum Tipo {
        CANCELAMENTO_POR_BLOQUEIO
    }

    public enum Status {
        PENDENTE,
        ACEITA_PELA_API,
        ENTREGUE,
        FALHA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agendamento_id", nullable = false)
    private Agendamento agendamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private String destinatario;

    @Column(nullable = false)
    private String phoneNumberId;

    @Column(nullable = false, columnDefinition = "text")
    private String mensagem;

    @Column(nullable = false)
    private int tentativas;

    @Column(nullable = false)
    private LocalDateTime criadaEm;

    private LocalDateTime ultimaTentativaEm;

    private String mensagemIdMeta;

    @Column(columnDefinition = "text")
    private String ultimoErro;

    public NotificacaoPendente(
            Agendamento agendamento,
            String mensagem
    ) {
        this.agendamento = agendamento;
        this.tipo = Tipo.CANCELAMENTO_POR_BLOQUEIO;
        this.status = Status.PENDENTE;
        this.destinatario = agendamento.getCliente().getNumeroTelefone();
        this.phoneNumberId =
                agendamento.getBarbeiro().getWhatsappPhoneNumberId();
        this.mensagem = mensagem;
        this.criadaEm = LocalDateTime.now();
    }

    public void registrarTentativa() {
        if (status != Status.PENDENTE && status != Status.FALHA) {
            throw new IllegalStateException(
                    "Essa notificação não está disponível para reenvio."
            );
        }

        tentativas++;
        ultimaTentativaEm = LocalDateTime.now();
        ultimoErro = null;
    }

    public void registrarAceite(String mensagemIdMeta) {
        if (mensagemIdMeta == null || mensagemIdMeta.isBlank()) {
            throw new IllegalArgumentException(
                    "A Meta não retornou o identificador da mensagem."
            );
        }

        this.mensagemIdMeta = mensagemIdMeta;
        this.status = Status.ACEITA_PELA_API;
        this.ultimoErro = null;
    }

    public void registrarFalha(String erro) {
        if (status == Status.ENTREGUE) {
            return;
        }

        this.status = Status.FALHA;
        this.ultimoErro = erro == null || erro.isBlank()
                ? "Falha sem detalhes."
                : erro;
    }

    public void registrarEntrega() {
        this.status = Status.ENTREGUE;
        this.ultimoErro = null;
    }

}