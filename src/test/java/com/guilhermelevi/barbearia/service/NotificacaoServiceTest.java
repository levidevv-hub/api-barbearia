package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificacaoServiceTest {

    @AfterEach
    void limparTransacao() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void exigeTransacaoAtiva() {
        NotificacaoService service =
                new NotificacaoService(mock(WhatsAppClient.class));

        assertThrows(
                IllegalStateException.class,
                () -> service.novoAgendamento(agendamento())
        );
    }

    @Test
    void enviaSomenteDepoisDoCommit() throws Exception {
        WhatsAppClient whatsapp = mock(WhatsAppClient.class);
        NotificacaoService service = new NotificacaoService(whatsapp);
        definirUsarTemplate(service, false);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.novoAgendamento(agendamento());

        verifyNoInteractions(whatsapp);

        var sincronizacoes =
                TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, sincronizacoes.size());

        sincronizacoes.get(0).afterCommit();

        verify(whatsapp).enviarTexto(
                eq("phone"),
                eq("5588111111111"),
                contains("NOVO AGENDAMENTO")
        );
    }

    @Test
    void semDestinatarioNaoRegistraEnvio() throws Exception {
        WhatsAppClient whatsapp = mock(WhatsAppClient.class);
        NotificacaoService service = new NotificacaoService(whatsapp);
        definirUsarTemplate(service, false);

        Agendamento agendamento = agendamento();
        agendamento.getBarbeiro().setNumeroWhatsAppNotificacao(" ");

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.agendamentoCancelado(agendamento);

        assertTrue(
                TransactionSynchronizationManager
                        .getSynchronizations()
                        .isEmpty()
        );
        verifyNoInteractions(whatsapp);
    }

    private static Agendamento agendamento() {
        Cliente cliente = Cliente.builder()
                .nomeCompleto("Cliente")
                .numeroTelefone("5588999999999")
                .build();

        Barbeiro barbeiro = Barbeiro.builder()
                .nome("Zalura")
                .whatsappPhoneNumberId("phone")
                .numeroWhatsAppNotificacao("5588111111111")
                .build();

        Servico servico = Servico.builder()
                .nome("Corte")
                .duracaoMinutos(30)
                .build();

        return new Agendamento(
                cliente,
                barbeiro,
                servico,
                LocalDateTime.of(2030, 1, 10, 10, 0)
        );
    }

    private static void definirUsarTemplate(
            NotificacaoService service,
            boolean valor
    ) throws Exception {
        Field campo = NotificacaoService.class.getDeclaredField("usarTemplate");
        campo.setAccessible(true);
        campo.set(service, valor);
    }
}
