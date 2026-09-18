package com.guilhermelevi.barbearia.domain;

import com.guilhermelevi.barbearia.domain.exception.OperacaoAdministrativaException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "previas_bloqueio")
public class PreviaBloqueio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barbeiro_id", nullable = false)
    private Barbeiro barbeiro;

    @Column(nullable = false)
    private String numeroAdministrador;

    @Column(nullable = false)
    private LocalDate data;

    @ElementCollection
    @CollectionTable(
            name = "previas_bloqueio_reservas",
            joinColumns = @JoinColumn(name = "previa_id"),
            uniqueConstraints = @UniqueConstraint(
                    columnNames = {"previa_id", "agendamento_id"}
            )
    )
    @Column(name = "agendamento_id", nullable = false)
    @Getter(AccessLevel.NONE)
    private Set<Long> idsAgendamentos = new HashSet<>();

    @Column(nullable = false)
    private LocalDateTime expiraEm;

    @Column(nullable = false)
    private boolean consumida;

    public PreviaBloqueio(
            Barbeiro barbeiro,
            String numeroAdministrador,
            LocalDate data,
            Set<Long> idsAgendamentos
    ) {
        this.barbeiro = barbeiro;
        this.numeroAdministrador = numeroAdministrador;
        this.data = data;
        this.idsAgendamentos = new HashSet<>(idsAgendamentos);
        this.expiraEm = LocalDateTime.now().plusMinutes(10);
        this.consumida = false;
    }

    public Set<Long> getIdsAgendamentos() {
        return Set.copyOf(idsAgendamentos);
    }

    public boolean estaExpirada() {
        return !LocalDateTime.now().isBefore(expiraEm);
    }

    public void validarParaConfirmar() {
        if (consumida || estaExpirada()) {
            throw new OperacaoAdministrativaException(
                    "Essa prévia já foi utilizada ou expirou. "
                            + "Solicite uma nova consulta."
            );
        }
    }

    public void consumir() {
        consumida = true;
    }
}