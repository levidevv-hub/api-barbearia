package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorAssinaturaWebhookTest {

    @Test
    void validaAssinaturaHmacCorreta() throws Exception {
        String secret = "segredo";
        byte[] corpo = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String assinatura = "sha256=" + HexFormat.of().formatHex(mac.doFinal(corpo));

        ValidadorAssinaturaWebhook validador = new ValidadorAssinaturaWebhook(secret);

        assertTrue(validador.validar(corpo, assinatura));
        assertFalse(validador.validar(corpo, "sha256=" + "0".repeat(64)));
    }

    @Test
    void rejeitaConfiguracaoEAssinaturasInvalidas() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ValidadorAssinaturaWebhook(" ")
        );

        ValidadorAssinaturaWebhook validador =
                new ValidadorAssinaturaWebhook("segredo");

        assertFalse(validador.validar(null, "sha256=" + "0".repeat(64)));
        assertFalse(validador.validar(new byte[0], null));
        assertFalse(validador.validar(new byte[0], "invalida"));
    }
}
