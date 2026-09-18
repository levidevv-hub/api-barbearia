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
    @Column(nullable = false, unique = true)
    private String whatsappPhoneNumberId;
    private String numeroWhatsAppNotificacao;
    @Column(name = "numero_whatsapp_administrador")
    private String numeroWhatsAppAdministrador;
    private LocalTime inicioExpediente;
    private LocalTime fimExpediente;

}