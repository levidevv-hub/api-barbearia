package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.dto.request.MetaEmbeddedSignupRequest;
import com.guilhermelevi.barbearia.service.ConexaoWhatsAppPendenteService;
import com.guilhermelevi.barbearia.service.MetaWhatsAppService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/meta/whatsapp")
@CrossOrigin(origins = "https://zaluratech.com.br")
public class MetaWhatsAppController {

    private final MetaWhatsAppService metaWhatsAppService;
    private final ConexaoWhatsAppPendenteService conexaoWhatsAppPendenteService;

    public MetaWhatsAppController(MetaWhatsAppService metaWhatsAppService, ConexaoWhatsAppPendenteService conexaoWhatsAppPendenteService) {
        this.metaWhatsAppService = metaWhatsAppService;
        this.conexaoWhatsAppPendenteService = conexaoWhatsAppPendenteService;
    }

    @PostMapping("/connect")
    public ResponseEntity<Void> conectar(
            @Valid @RequestBody MetaEmbeddedSignupRequest request
    ) {

        metaWhatsAppService.conectar(request);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/link/{barbeiroId}")
    public ResponseEntity<String> gerarLink(
            @PathVariable Long barbeiroId
    ) {

        var token =
                conexaoWhatsAppPendenteService
                        .gerarToken(barbeiroId);

        String link =
                "https://zaluratech.com.br/conectar-whatsapp?token="
                        + token;

        return ResponseEntity.ok(link);
    }

    @GetMapping("/validar-token/{token}")
    public ResponseEntity<Void> validarToken(
            @PathVariable UUID token
    ) {

        conexaoWhatsAppPendenteService.buscarValida(token);

        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> tratarArgumentoInvalido(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "erro", exception.getMessage()
                ));
    }
}