package com.guilhermelevi.barbearia.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "mensagens_atendimento", indexes = @Index(name = "idx_mensagem_atendimento", columnList = "atendimento_id,id"))
public class MensagemAtendimento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private AtendimentoHumano atendimento;
    @Column(nullable = false) private String autor;
    @Column(nullable = false, columnDefinition = "text") private String texto;
    @Column(nullable = false) private LocalDateTime criadaEm = LocalDateTime.now();
    private String mensagemIdMeta;
}
