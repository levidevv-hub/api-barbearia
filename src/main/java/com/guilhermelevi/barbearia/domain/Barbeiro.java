package com.guilhermelevi.barbearia.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "barbeiros")
@Builder
public class Barbeiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;

    @Builder.Default
    @Column(name = "robo_ativo", nullable = false, columnDefinition = "boolean default true")
    private boolean roboAtivo = true;

    @Column(length = 2048)
    private String whatsappAccessToken;
    @Column(unique = true)
    private String whatsappPhoneNumberId;
    private String whatsappWabaId;
    private String numeroWhatsAppNotificacao;
    @Column(name = "numero_whatsapp_administrador")
    private String numeroWhatsAppAdministrador;

    private LocalTime inicioExpediente;
    private LocalTime fimExpediente;

    private Double latitude;
    private Double longitude;
    private String endereco;

}
