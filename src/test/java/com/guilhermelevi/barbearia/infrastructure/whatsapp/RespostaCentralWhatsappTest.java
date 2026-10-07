package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RespostaCentralWhatsappTest {
    @AfterEach void limparTransacao() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    @Test void escopoNaoVazaEntreRequisicoesENemDepoisDeExcecao() {
        var body = Map.<String, Object>of("interactive", Map.of("id", "ADMIN_PAUSAR_ROBO"));
        try (var a = RespostaCentralWhatsapp.abrir("990000", 1L)) {
            assertEquals("990000", RespostaCentralWhatsapp.preparar("linha", body).phoneNumberId());
            assertThrows(IllegalStateException.class, () -> {
                try (var b = RespostaCentralWhatsapp.abrir("880000", 2L)) {
                    assertEquals("880000", RespostaCentralWhatsapp.preparar("linha", body).phoneNumberId());
                    throw new IllegalStateException("falha");
                }
            });
            assertEquals("990000", RespostaCentralWhatsapp.preparar("linha", body).phoneNumberId());
        }
        assertEquals("linha", RespostaCentralWhatsapp.preparar("linha", body).phoneNumberId());
        assertEquals("ADMIN_PAUSAR_ROBO", ((Map<?, ?>) body.get("interactive")).get("id"));
    }
    @Test void aposCommitUsaLinhaCredencialEIdsCapturadosMesmoDepoisDeMudarContexto() {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        var repo = mock(IBarbeiroRepository.class);
        when(repo.findByWhatsappPhoneNumberId("990000")).thenReturn(Optional.of(Barbeiro.builder().whatsappAccessToken("token-central").build()));
        var client = new WhatsAppClient(builder, repo); ReflectionTestUtils.setField(client, "apiVersion", "v26.0");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        server.expect(requestTo("https://graph.facebook.com/v26.0/990000/messages"))
                .andExpect(header("Authorization", "Bearer token-central"))
                .andExpect(request -> {
                    var json = JsonMapper.builder().build().readTree(((MockClientHttpRequest) request).getBodyAsString());
                    var rows = json.path("interactive").path("action").path("sections").get(0).path("rows");
                    assertEquals(8, rows.size());
                    for (var row : rows) assertTrue(row.path("id").asText().startsWith("CENTRAL_41_ADMIN_"));
                    assertEquals("5511980000001", json.path("to").asText());
                }).andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.central\"}]}", MediaType.APPLICATION_JSON));
        try (var contexto = RespostaCentralWhatsapp.abrir("990000", 41L)) {
            client.enviarMenuAdministrador("linha-barbeiro", "5511980000001");
        }
        verifyNoInteractions(repo);
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        try (var outro = RespostaCentralWhatsapp.abrir("880000", 42L)) {
            TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        }
        server.verify();
        verify(repo, never()).findByWhatsappPhoneNumberId("linha-barbeiro");
    }
    @Test void botoesDeControleEPausaUsamContextoDaAgenda() {
        try (var escopo = RespostaCentralWhatsapp.abrir("990000", 12L)) {
            var body = Map.<String, Object>of("interactive", Map.of("action", Map.of("buttons", List.of(
                    Map.of("reply", Map.of("id", "ADMIN_CONFIRMAR_PAUSA_ROBO")),
                    Map.of("reply", Map.of("id", "ADMIN_VOLTAR_CONTROLE_ROBO"))))));
            var envio = RespostaCentralWhatsapp.preparar("linha", body);
            var json = JsonMapper.builder().build().valueToTree(envio.body());
            assertEquals("CENTRAL_12_ADMIN_CONFIRMAR_PAUSA_ROBO", json.path("interactive").path("action").path("buttons").get(0).path("reply").path("id").asText());
            assertEquals("CENTRAL_12_ADMIN_VOLTAR_CONTROLE_ROBO", json.path("interactive").path("action").path("buttons").get(1).path("reply").path("id").asText());
        }
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void paginacaoRespeitaLimiteDeDezOpcoesENomesDe24Caracteres(int pagina) {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        var repo = mock(IBarbeiroRepository.class);
        when(repo.findByWhatsappPhoneNumberId("990000")).thenReturn(Optional.of(Barbeiro.builder().whatsappAccessToken("token-central").build()));
        var client = new WhatsAppClient(builder, repo); ReflectionTestUtils.setField(client, "apiVersion", "v26.0");
        var barbearias = new ArrayList<Barbeiro>();
        for (long i = 1; i <= 23; i++) barbearias.add(Barbeiro.builder().id(i).nome("Nome de barbearia muito longo " + i).build());
        server.expect(requestTo("https://graph.facebook.com/v26.0/990000/messages"))
                .andExpect(request -> {
                    var json = JsonMapper.builder().build().readTree(((MockClientHttpRequest) request).getBodyAsString());
                    var rows = json.path("interactive").path("action").path("sections").get(0).path("rows");
                    assertTrue(rows.size() <= 10);
                    long esperado = pagina * 8L + 1;
                    assertEquals("CENTRAL_SELECIONAR_" + esperado, rows.get(0).path("id").asText());
                    for (var row : rows) assertTrue(row.path("title").asText().length() <= 24);
                }).andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.selecao\"}]}", MediaType.APPLICATION_JSON));
        client.enviarSelecaoBarbeariasCentral("990000", "5511980000001", barbearias, pagina);
        server.verify();
    }
}
