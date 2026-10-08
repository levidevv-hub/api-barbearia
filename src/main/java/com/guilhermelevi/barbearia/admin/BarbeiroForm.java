package com.guilhermelevi.barbearia.admin;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalTime;

@Getter @Setter
public class BarbeiroForm {
    @NotBlank(message = "Informe o nome.") @Size(max = 120)
    private String nome;
    @Size(max = 80)
    private String segmento;
    @NotBlank(message = "Informe o WhatsApp do administrador.")
    @Pattern(regexp = "[1-9][0-9]{7,14}", message = "Use apenas digitos, com codigo do pais e DDD.")
    private String numeroWhatsAppAdministrador;
    @NotBlank(message = "Informe o numero para notificacoes.")
    @Pattern(regexp = "[1-9][0-9]{7,14}", message = "Use apenas digitos, com codigo do pais e DDD.")
    private String numeroWhatsAppNotificacao;
    @NotNull(message = "Informe o inicio.") @DateTimeFormat(pattern = "HH:mm")
    private LocalTime inicioExpediente;
    @NotNull(message = "Informe o fim.") @DateTimeFormat(pattern = "HH:mm")
    private LocalTime fimExpediente;
    @Size(max = 255)
    private String endereco;
}
