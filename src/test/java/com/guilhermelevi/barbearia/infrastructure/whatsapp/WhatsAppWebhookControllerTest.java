package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.service.IStatusForwardingInboxService;
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
        IStatusForwardingInboxService forwarding = mock(IStatusForwardingInboxService.class);
        ValidadorAssinaturaWebhook assinatura = mock(ValidadorAssinaturaWebhook.class);

        WhatsAppWebhookController controller = new WhatsAppWebhookController(
                lote, status, assinatura, JsonMapper.builder().build(), forwarding
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
        IStatusForwardingInboxService forwarding = mock(IStatusForwardingInboxService.class);
        ValidadorAssinaturaWebhook assinatura = mock(ValidadorAssinaturaWebhook.class);

        byte[] corpo = "{}".getBytes(StandardCharsets.UTF_8);
        when(assinatura.validar(corpo, "assinatura")).thenReturn(false);

        WhatsAppWebhookController controller = new WhatsAppWebhookController(
                lote, status, assinatura, JsonMapper.builder().build(), forwarding
        );

        var resposta = controller.receber("assinatura", corpo);

        assertEquals(HttpStatus.FORBIDDEN, resposta.getStatusCode());
        verifyNoInteractions(lote, status, forwarding);
    }

    @Test
    void postValidoProcessaStatusEMensagens() {
        LoteMensagensService lote = mock(LoteMensagensService.class);
        RecebimentoStatusService status = mock(RecebimentoStatusService.class);
        IStatusForwardingInboxService forwarding = mock(IStatusForwardingInboxService.class);
        ValidadorAssinaturaWebhook assinatura = mock(ValidadorAssinaturaWebhook.class);

        byte[] corpo = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        when(assinatura.validar(corpo, "assinatura")).thenReturn(true);

        WhatsAppWebhookController controller = new WhatsAppWebhookController(
                lote, status, assinatura, JsonMapper.builder().build(), forwarding
        );

        var resposta = controller.receber("assinatura", corpo);

        assertEquals(200, resposta.getStatusCode().value());
        var order = inOrder(forwarding, status, lote);
        order.verify(forwarding).receive(same(corpo), eq("assinatura"), any());
        order.verify(status).receber(any());
        verify(lote).processar(any());
    }

    @Test
    void persistenceFailureReturns503WithoutProcessingBot() {
        var lote = mock(LoteMensagensService.class);
        var status = mock(RecebimentoStatusService.class);
        var signature = mock(ValidadorAssinaturaWebhook.class);
        var forwarding = mock(IStatusForwardingInboxService.class);
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        when(signature.validar(body, "signature")).thenReturn(true);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"))
                .when(forwarding).receive(any(), any(), any());
        var controller = new WhatsAppWebhookController(lote, status, signature,
                JsonMapper.builder().build(), forwarding);
        assertEquals(503, controller.receber("signature", body).getStatusCode().value());
        verifyNoInteractions(lote, status);
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

