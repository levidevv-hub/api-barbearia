package com.guilhermelevi.barbearia.domain;

import com.guilhermelevi.barbearia.domain.enums.StatusAgendamentoEnum;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agendamentos")
@Builder
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(optional = false)
    @JoinColumn(name = "barbeiro_id")
    private Barbeiro barbeiro;

    @ManyToOne(optional = false)
    @JoinColumn(name = "servico_id")
    private Servico servico;

    private LocalDateTime inicio;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StatusAgendamentoEnum status = StatusAgendamentoEnum.CONFIRMADO;

    @Column(name = "duracao_minutos", nullable = false)
    private Integer duracaoMinutos;

    public Agendamento(Cliente cliente, Barbeiro barbeiro, Servico servico, LocalDateTime inicio) {
        this.cliente = cliente;
        this.barbeiro = barbeiro;
        this.servico = servico;
        this.inicio = inicio;
        this.status = StatusAgendamentoEnum.CONFIRMADO;
        this.duracaoMinutos = servico.getDuracaoMinutos();
    }

    public LocalDateTime calcularFim() {
        if (duracaoMinutos == null || duracaoMinutos <= 0) {
            throw new IllegalStateException(
                    "O agendamento está sem uma duração válida."
            );
        }

        return inicio.plusMinutes(duracaoMinutos);
    }

    @PrePersist
    private void prepararNovoAgendamento() {
        if (status == null) {
            status = StatusAgendamentoEnum.CONFIRMADO;
        }

        validarDuracao();
    }

    @PreUpdate
    private void validarDuracao() {
        if (duracaoMinutos == null || duracaoMinutos <= 0) {
            throw new IllegalArgumentException(
                    "A duração do agendamento deve ser positiva."
            );
        }
    }

}