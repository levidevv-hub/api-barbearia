package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.domain.Agendamento;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import java.util.List;
import com.guilhermelevi.barbearia.domain.Barbeiro;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.Optional;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
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
        IBarbeiroRepository barbeiroRepository =
                mock(IBarbeiroRepository.class);
        RestClient.Builder builder = mock(RestClient.Builder.class);
        RestClient restClient = mock(RestClient.class);

        when(builder.baseUrl("https://graph.facebook.com"))
                .thenReturn(builder);
        when(builder.build()).thenReturn(restClient);

        WhatsAppClient client =
                new WhatsAppClient(builder, barbeiroRepository);

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
        IBarbeiroRepository barbeiroRepository =
                mock(IBarbeiroRepository.class);
        RestClient.Builder builder = mock(RestClient.Builder.class);
        RestClient restClient = mock(RestClient.class);

        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);

        WhatsAppClient client =
                new WhatsAppClient(builder, barbeiroRepository);

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

    @Test
    void enviaLocalizacaoDaBarbearia() {

        RestClient.Builder builder = RestClient.builder();

        MockRestServiceServer server =
                MockRestServiceServer
                        .bindTo(builder)
                        .build();

        IBarbeiroRepository barbeiroRepository =
                mock(IBarbeiroRepository.class);

        Barbeiro barbeiro = Barbeiro.builder()
                .nome("Zalura")
                .latitude(-4.92402)
                .longitude(-37.97467)
                .whatsappAccessToken("token-teste")
                .build();

        when(
                barbeiroRepository.findByWhatsappPhoneNumberId("phone")
        ).thenReturn(Optional.of(barbeiro));

        WhatsAppClient client =
                new WhatsAppClient(
                        builder,
                        barbeiroRepository
                );

        ReflectionTestUtils.setField(
                client,
                "apiVersion",
                "v26.0"
        );

        server.expect(
                        requestTo(
                                "https://graph.facebook.com/v26.0/phone/messages"
                        )
                )
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        "Authorization",
                        "Bearer token-teste"
                ))
                .andExpect(content().json("""
            {
              "messaging_product": "whatsapp",
              "to": "5588999999999",
              "type": "location",
              "location": {
                "latitude": -4.92402,
                "longitude": -37.97467,
                "name": "Zalura"
              }
            }
            """))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "messages": [
                                    {
                                      "id": "wamid.teste"
                                    }
                                  ]
                                }
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        client.enviarLocalizacao(
                "phone",
                "5588999999999",
                barbeiro
        );

        server.verify();
    }
}
