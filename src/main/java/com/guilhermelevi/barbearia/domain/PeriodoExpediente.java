package com.guilhermelevi.barbearia.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "periodos_expediente",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_periodo_expediente_inicio",
                columnNames = {"expediente_semanal_id", "inicio"}
        )
)
public class PeriodoExpediente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expediente_semanal_id", nullable = false)
    private ExpedienteSemanal expedienteSemanal;

    @Column(nullable = false)
    private LocalTime inicio;

    @Column(nullable = false)
    private LocalTime fim;

    @PrePersist
    @PreUpdate
    private void validarPeriodo() {
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            throw new IllegalArgumentException(
                    "Informe início e fim, com o fim depois do início."
            );
        }
    }
}