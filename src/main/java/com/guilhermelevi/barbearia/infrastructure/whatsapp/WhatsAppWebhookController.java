package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.service.ConversaService;
import com.guilhermelevi.barbearia.service.LoteMensagensService;
import com.guilhermelevi.barbearia.service.RecebimentoStatusService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import com.guilhermelevi.barbearia.service.RecebimentoStatusService;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsAppWebhookController {

    private final LoteMensagensService loteMensagensService;
    private final RecebimentoStatusService recebimentoStatusService;
    private final ValidadorAssinaturaWebhook validadorAssinatura;
    private final ObjectMapper objectMapper;

    @Value("${whatsapp.verify-token}")
    private String verifyToken;

    public WhatsAppWebhookController(
            LoteMensagensService loteMensagensService,
            RecebimentoStatusService recebimentoStatusService, ValidadorAssinaturaWebhook validadorAssinatura, ObjectMapper objectMapper
    ) {
        this.loteMensagensService = loteMensagensService;
        this.recebimentoStatusService = recebimentoStatusService;
        this.validadorAssinatura = validadorAssinatura;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<String> verificar(@RequestParam("hub.mode") String mode,
                                            @RequestParam("hub.verify_token") String token,
                                            @RequestParam("hub.challenge") String challenge) {
        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            return ResponseEntity.ok(challenge);
        }

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .build();
    }

    @PostMapping
    public ResponseEntity<Void> receber(
            @RequestHeader(
                    value = "X-Hub-Signature-256",
                    required = false
            ) String assinatura,
            @RequestBody byte[] corpo
    ) {
        if (!validadorAssinatura.validar(corpo, assinatura)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .build();
        }

        JsonNode payload = objectMapper.readTree(corpo);

        recebimentoStatusService.receber(payload);
        loteMensagensService.processar(payload);

        return ResponseEntity.ok().build();
    }
}
