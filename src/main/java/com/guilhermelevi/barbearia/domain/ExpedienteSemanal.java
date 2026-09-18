package com.guilhermelevi.barbearia.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "expedientes_semanais",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_expediente_barbeiro_dia",
                columnNames = {"barbeiro_id", "dia_semana"}
        )
)
public class ExpedienteSemanal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barbeiro_id", nullable = false)
    private Barbeiro barbeiro;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false)
    private DayOfWeek diaSemana;

    @Column(nullable = false)
    private boolean aberto;

    private LocalTime inicio;

    private LocalTime fim;

    @PrePersist
    @PreUpdate
    private void validarExpediente() {
        if (diaSemana == null) {
            throw new IllegalArgumentException(
                    "O dia da semana é obrigatório."
            );
        }

        if (aberto && (inicio == null || fim == null
                || !fim.isAfter(inicio))) {
            throw new IllegalArgumentException(
                    "Para um dia aberto, informe início e fim, "
                            + "com o fim depois do início."
            );
        }
    }
}