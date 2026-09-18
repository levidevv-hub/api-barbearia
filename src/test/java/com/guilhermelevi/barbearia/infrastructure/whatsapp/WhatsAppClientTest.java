package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.domain.Agendamento;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsAppClientTest {

    @AfterEach
    void limparTransacao() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void listaVaziaAgendaRespostaParaDepoisDoCommit() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        RestClient restClient = mock(RestClient.class);

        when(builder.baseUrl("https://graph.facebook.com"))
                .thenReturn(builder);
        when(builder.build()).thenReturn(restClient);

        WhatsAppClient client = new WhatsAppClient(builder);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        client.enviarMeusHorarios(
                "phone",
                "5588999999999",
                List.<Agendamento>of()
        );

        assertEquals(
                1,
                TransactionSynchronizationManager
                        .getSynchronizations()
                        .size()
        );

        verifyNoInteractions(restClient);
    }

    @Test
    void transacaoSemSincronizacaoEhRejeitada() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        RestClient restClient = mock(RestClient.class);

        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);

        WhatsAppClient client = new WhatsAppClient(builder);

        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThrows(
                IllegalStateException.class,
                () -> client.enviarTextoAposCommit(
                        "phone",
                        "5588999999999",
                        "teste"
                )
        );
    }
}
