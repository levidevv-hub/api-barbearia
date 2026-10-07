package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.domain.Agendamento;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void controleEnviaBotaoCompativelComWhatsAppEComEstado(boolean ativo) {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var repository = mock(IBarbeiroRepository.class);
        when(repository.findByWhatsappPhoneNumberId("phone"))
                .thenReturn(Optional.of(Barbeiro.builder().whatsappAccessToken("teste").build()));
        var client = new WhatsAppClient(builder, repository);
        ReflectionTestUtils.setField(client, "apiVersion", "v26.0");
        String id = ativo ? "ADMIN_PAUSAR_ROBO" : "ADMIN_REATIVAR_ROBO";
        String titulo = ativo ? "Pausar robô" : "Reativar robô";
        server.expect(requestTo("https://graph.facebook.com/v26.0/phone/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"type":"interactive","interactive":{"type":"button","action":{"buttons":[
                          {"type":"reply","reply":{"id":"%s","title":"%s"}}
                        ]}}}
                        """.formatted(id, titulo)))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.controle\"}]}", MediaType.APPLICATION_JSON));
        client.enviarControleRobo("phone", "5588912345678", ativo);
        server.verify();
    }

    @Test
    void confirmacaoOferecePausaEVoltarComIdsDistintos() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var repository = mock(IBarbeiroRepository.class);
        when(repository.findByWhatsappPhoneNumberId("phone"))
                .thenReturn(Optional.of(Barbeiro.builder().whatsappAccessToken("teste").build()));
        var client = new WhatsAppClient(builder, repository);
        ReflectionTestUtils.setField(client, "apiVersion", "v26.0");
        server.expect(requestTo("https://graph.facebook.com/v26.0/phone/messages"))
                .andExpect(content().json("""
                        {"type":"interactive","interactive":{"type":"button","action":{"buttons":[
                          {"type":"reply","reply":{"id":"ADMIN_CONFIRMAR_PAUSA_ROBO","title":"Sim, pausar"}},
                          {"type":"reply","reply":{"id":"ADMIN_VOLTAR_CONTROLE_ROBO","title":"Voltar"}}
                        ]}}}
                        """))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.confirmacao\"}]}", MediaType.APPLICATION_JSON));
        client.enviarConfirmacaoPausaRobo("phone", "5588912345678");
        server.verify();
    }

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
