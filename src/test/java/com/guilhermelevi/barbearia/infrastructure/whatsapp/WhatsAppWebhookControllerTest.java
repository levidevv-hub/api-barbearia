package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.service.LoteMensagensService;
import com.guilhermelevi.barbearia.service.RecebimentoStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsAppWebhookControllerTest {

    @Test
    void verificaTokenCorretoERejeitaIncorreto() throws Exception {
        LoteMensagensService lote = mock(LoteMensagensService.class);
        RecebimentoStatusService status = mock(RecebimentoStatusService.class);
        ValidadorAssinaturaWebhook assinatura = mock(ValidadorAssinaturaWebhook.class);

        WhatsAppWebhookController controller = new WhatsAppWebhookController(
                lote, status, assinatura, JsonMapper.builder().build()
        );
        definirVerifyToken(controller, "token-correto");

        var ok = controller.verificar("subscribe", "token-correto", "123");
        var negado = controller.verificar("subscribe", "errado", "123");

        assertEquals(200, ok.getStatusCode().value());
        assertEquals("123", ok.getBody());
        assertEquals(HttpStatus.FORBIDDEN, negado.getStatusCode());
    }

    @Test
    void postRejeitaAssinaturaInvalidaSemProcessar() {
        LoteMensagensService lote = mock(LoteMensagensService.class);
        RecebimentoStatusService status = mock(RecebimentoStatusService.class);
        ValidadorAssinaturaWebhook assinatura = mock(ValidadorAssinaturaWebhook.class);

        byte[] corpo = "{}".getBytes(StandardCharsets.UTF_8);
        when(assinatura.validar(corpo, "assinatura")).thenReturn(false);

        WhatsAppWebhookController controller = new WhatsAppWebhookController(
                lote, status, assinatura, JsonMapper.builder().build()
        );

        var resposta = controller.receber("assinatura", corpo);

        assertEquals(HttpStatus.FORBIDDEN, resposta.getStatusCode());
        verifyNoInteractions(lote, status);
    }

    @Test
    void postValidoProcessaStatusEMensagens() {
        LoteMensagensService lote = mock(LoteMensagensService.class);
        RecebimentoStatusService status = mock(RecebimentoStatusService.class);
        ValidadorAssinaturaWebhook assinatura = mock(ValidadorAssinaturaWebhook.class);

        byte[] corpo = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        when(assinatura.validar(corpo, "assinatura")).thenReturn(true);

        WhatsAppWebhookController controller = new WhatsAppWebhookController(
                lote, status, assinatura, JsonMapper.builder().build()
        );

        var resposta = controller.receber("assinatura", corpo);

        assertEquals(200, resposta.getStatusCode().value());
        verify(status).receber(any());
        verify(lote).processar(any());
    }

    private static void definirVerifyToken(
            WhatsAppWebhookController controller,
            String valor
    ) throws Exception {
        Field campo = WhatsAppWebhookController.class.getDeclaredField("verifyToken");
        campo.setAccessible(true);
        campo.set(controller, valor);
    }
}
