package com.guilhermelevi.barbearia.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "sessoes_central_whatsapp")
@Getter @Setter @NoArgsConstructor
public class SessaoCentralWhatsapp {
    @Id @Column(length = 64)
    private String chave;
    // Sem FK: uma seleção antiga nunca impede excluir um cadastro sem histórico.
    // O alvo e a autorização são consultados novamente antes de cada operação.
    private Long barbeiroId;
    @Column(nullable = false)
    private Instant atualizadaEm;

    public SessaoCentralWhatsapp(String chave, Long barbeiroId) {
        this.chave = chave;
        selecionar(barbeiroId);
    }

    public void selecionar(Long barbeiroId) {
        this.barbeiroId = barbeiroId;
        this.atualizadaEm = Instant.now();
    }
}
