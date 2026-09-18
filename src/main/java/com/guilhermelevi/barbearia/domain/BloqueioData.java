package com.guilhermelevi.barbearia.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "bloqueios_datas",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bloqueio_barbeiro_data",
                columnNames = {"barbeiro_id", "data"}
        )
)
public class BloqueioData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barbeiro_id", nullable = false)
    private Barbeiro barbeiro;

    @Column(nullable = false)
    private LocalDate data;

    private String motivo;
}