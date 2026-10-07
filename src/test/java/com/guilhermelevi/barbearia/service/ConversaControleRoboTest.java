package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.repositories.*;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversaControleRoboTest {
    private static final String ADMIN = "5588912345678";
    private static final String CLIENTE = "5588987654321";
    private final IBarbeiroRepository barbeiros = mock(IBarbeiroRepository.class);
    private final ClienteService clientes = mock(ClienteService.class);
    private final ConversaAgendamentoService agendamento = mock(ConversaAgendamentoService.class);
    private final WhatsAppClient whatsapp = mock(WhatsAppClient.class);
    private final AutorizacaoBarbeiroService autorizacao = new AutorizacaoBarbeiroService(barbeiros);
    private final ControleRoboService controle = new ControleRoboService(barbeiros, autorizacao);
    private final ConversaAdminService admin = new ConversaAdminService(
            autorizacao, mock(BloqueioDataService.class), mock(PreviaBloqueioService.class),
            mock(AgendamentoService.class), mock(ConversaServicoAdminService.class), whatsapp, controle);
    private final ConversaService conversa = new ConversaService(barbeiros, clientes, agendamento, admin, autorizacao);
    private Barbeiro barbeiro;

    @BeforeEach
    void preparar() {
        barbeiro = Barbeiro.builder().id(1L).whatsappPhoneNumberId("phone")
                .numeroWhatsAppAdministrador(ADMIN).build();
        when(barbeiros.findByWhatsappPhoneNumberId("phone")).thenReturn(Optional.of(barbeiro));
        when(barbeiros.findById(1L)).thenReturn(Optional.of(barbeiro));
        when(barbeiros.buscarParaAgendar(1L)).thenReturn(Optional.of(barbeiro));
    }

    @Test
    void statusEConfirmacaoNaoPausamEVoltarMantemAtivo() {
        conversa.processar(clique(ADMIN, "ADMIN_CONTROLE_ROBO"));
        conversa.processar(clique(ADMIN, "ADMIN_PAUSAR_ROBO"));
        conversa.processar(clique(ADMIN, "ADMIN_VOLTAR_CONTROLE_ROBO"));

        assertTrue(barbeiro.isRoboAtivo());
        verify(whatsapp, times(2)).enviarControleRobo("phone", ADMIN, true);
        verify(whatsapp).enviarConfirmacaoPausaRobo("phone", ADMIN);
        verify(barbeiros, never()).buscarParaAgendar(1L);
        verifyNoInteractions(agendamento);
    }

    @Test
    void pausaSomenteNaConfirmacaoEAdministradorReativaPeloMesmoMenu() {
        conversa.processar(clique(ADMIN, "ADMIN_CONFIRMAR_PAUSA_ROBO"));
        assertFalse(barbeiro.isRoboAtivo());
        verify(whatsapp).enviarControleRobo("phone", ADMIN, false);

        conversa.processar(texto(ADMIN, "Minha agenda"));
        verify(whatsapp).enviarMenuAdministrador("phone", ADMIN);
        conversa.processar(clique(ADMIN, "ADMIN_CONTROLE_ROBO"));
        verify(whatsapp, times(2)).enviarControleRobo("phone", ADMIN, false);
        conversa.processar(clique(ADMIN, "ADMIN_REATIVAR_ROBO"));
        assertTrue(barbeiro.isRoboAtivo());
        verify(whatsapp).enviarControleRobo("phone", ADMIN, true);
        verifyNoInteractions(agendamento);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN_CONTROLE_ROBO", "ADMIN_PAUSAR_ROBO", "ADMIN_CONFIRMAR_PAUSA_ROBO", "ADMIN_REATIVAR_ROBO"})
    void clienteNaoPodeControlarRobo(String acao) {
        conversa.processar(clique(CLIENTE, acao));
        assertTrue(barbeiro.isRoboAtivo());
        verify(barbeiros, never()).buscarParaAgendar(1L);
        verifyNoInteractions(agendamento);
        verify(whatsapp).enviarTextoAposCommit("phone", CLIENTE,
                "Esse número não tem permissão para administrar esta agenda.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AGENDAR", "CONFIRMAR_qualquer", "CANCELAR_1", "LOCALIZACAO", "ADMIN_REATIVAR_ROBO"})
    void pausaIgnoraBotoesDeClienteSemCadastrarOuMudarAgendamento(String acao) {
        barbeiro.setRoboAtivo(false);
        conversa.processar(clique(CLIENTE, acao));
        assertFalse(barbeiro.isRoboAtivo());
        verifyNoInteractions(clientes, agendamento, whatsapp);
    }

    @Test
    void pausaIgnoraTextoEListaInclusiveClienteTentandoMinhaAgenda() {
        barbeiro.setRoboAtivo(false);
        conversa.processar(texto(CLIENTE, "oi"));
        conversa.processar(texto(CLIENTE, "Minha agenda"));
        conversa.processar(payload(CLIENTE, "\"type\":\"interactive\",\"interactive\":{\"list_reply\":{\"id\":\"SERVICO_1\"}}"));
        verifyNoInteractions(clientes, agendamento, whatsapp);
    }

    @Test
    void administradorNaoIniciaAgendamentoComMensagemComumDurantePausa() {
        barbeiro.setRoboAtivo(false);
        conversa.processar(texto(ADMIN, "oi"));
        conversa.processar(clique(ADMIN, "AGENDAR"));
        verifyNoInteractions(agendamento, whatsapp);
    }

    @Test
    void outraBarbeariaContinuaAtendendoEReativacaoRestauraFluxo() {
        barbeiro.setRoboAtivo(false);
        Barbeiro outro = Barbeiro.builder().id(2L).whatsappPhoneNumberId("outro").build();
        when(barbeiros.findByWhatsappPhoneNumberId("outro")).thenReturn(Optional.of(outro));
        Cliente cliente = new Cliente();
        when(clientes.buscarOuCriar("Cliente", CLIENTE)).thenReturn(cliente);
        JsonNode mensagem = texto(CLIENTE, "oi");
        conversa.processar(JsonMapper.builder().build().readTree(mensagem.toString().replace("\"phone\"", "\"outro\"")));
        verify(agendamento).iniciar(outro, cliente, CLIENTE);
        assertTrue(outro.isRoboAtivo());
        assertFalse(barbeiro.isRoboAtivo());
        conversa.processar(clique(ADMIN, "ADMIN_REATIVAR_ROBO"));
        conversa.processar(mensagem);
        verify(agendamento).iniciar(barbeiro, cliente, CLIENTE);
    }

    @Test
    void mensagemIgnoradaConcluiProcessamentoENaoEhReenviadaDepoisDeReativar() {
        var registros = mock(IMensagemRecebidaRepository.class);
        var processamento = new ProcessamentoMensagemService(registros, conversa);
        var mensagem = texto(CLIENTE, "oi");
        var registro = new MensagemRecebida("phone", "wamid.1", CLIENTE, mensagem.toString());
        when(registros.buscarParaProcessar("phone", "wamid.1")).thenReturn(Optional.of(registro));
        barbeiro.setRoboAtivo(false);
        processamento.processar(mensagem);
        assertEquals(MensagemRecebida.Status.PROCESSADA, registro.getStatus());
        barbeiro.setRoboAtivo(true);
        processamento.processar(mensagem);
        verifyNoInteractions(clientes, agendamento, whatsapp);
    }

    private JsonNode clique(String from, String id) {
        return payload(from, "\"type\":\"interactive\",\"interactive\":{\"button_reply\":{\"id\":\"" + id + "\"}}");
    }
    private JsonNode texto(String from, String texto) {
        return payload(from, "\"type\":\"text\",\"text\":{\"body\":\"" + texto + "\"}");
    }
    private JsonNode payload(String from, String mensagem) {
        return JsonMapper.builder().build().readTree("""
                {"entry":[{"changes":[{"value":{
                  "metadata":{"phone_number_id":"phone"},
                  "contacts":[{"profile":{"name":"Cliente"}}],
                  "messages":[{"id":"wamid.1","from":"%s",%s}]
                }}]}]}
                """.formatted(from, mensagem));
    }
}
