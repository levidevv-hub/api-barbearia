package com.guilhermelevi.barbearia.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "atendimentos_humanos", uniqueConstraints = @UniqueConstraint(
        name = "uk_atendimento_barbeiro_numero", columnNames = {"barbeiro_id", "numero_cliente"}))
public class AtendimentoHumano {
    public enum Estado { AUTOMATICO, AGUARDANDO, EM_ATENDIMENTO }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Barbeiro barbeiro;
    @Column(nullable = false) private String numeroCliente;
    private String nomeCliente;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Estado estado = Estado.AUTOMATICO;
    private String responsavel;
    private LocalDateTime solicitadoEm;
    private LocalDateTime ultimaMensagemClienteEm;
    @Column(columnDefinition = "text") private String erroAviso;
    private String avisoMensagemId;
    private String avisoPhoneNumberId;
}
