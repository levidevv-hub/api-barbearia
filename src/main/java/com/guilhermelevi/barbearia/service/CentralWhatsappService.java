package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CentralWhatsappService {
    private final IBarbeiroRepository barbeiros;

    @Value("${app.whatsapp.central.phone-number-id:}")
    private String phoneNumberId;

    @Value("${app.whatsapp.central.numero:}")
    private String numero;

    @PostConstruct
    public void validarConfiguracao() {
        phoneNumberId = phoneNumberId == null ? "" : phoneNumberId.strip();
        numero = numero == null ? "" : numero.strip();
        if (phoneNumberId.isBlank() && numero.isBlank()) return;
        if (!phoneNumberId.matches("[0-9]+") || !numero.matches("[1-9][0-9]{7,14}")) {
            throw new IllegalStateException("Configure CENTRAL_WHATSAPP_PHONE_NUMBER_ID e CENTRAL_WHATSAPP_NUMERO juntos, apenas com dígitos.");
        }
    }

    public boolean ehRemetenteCentral(String remetente) {
        return numero != null && !numero.isBlank() && remetente != null
                && numeroCanal(numero).equals(numeroCanal(remetente));
    }

    // Só para reconhecer a própria linha e evitar loops; autorização continua
    // usando igualdade exata do remetente cadastrado, sem aproximação.
    private String numeroCanal(String valor) {
        if (valor.length() == 13 && valor.startsWith("55") && valor.charAt(4) == '9') {
            return valor.substring(0, 4) + valor.substring(5);
        }
        return valor;
    }

    public boolean ehCentral(String recebido) {
        return phoneNumberId != null && !phoneNumberId.isBlank() && phoneNumberId.equals(recebido);
    }

    public String linhaNotificacao(String linhaBarbeiro) {
        if (phoneNumberId == null || phoneNumberId.isBlank()) return linhaBarbeiro;
        if (barbeiros.findByWhatsappPhoneNumberId(phoneNumberId).isEmpty()) {
            throw new IllegalStateException("A linha da central WhatsApp não está cadastrada no painel.");
        }
        return phoneNumberId;
    }
}
