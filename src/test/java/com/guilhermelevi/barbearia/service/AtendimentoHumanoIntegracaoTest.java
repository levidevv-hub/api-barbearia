package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.domain.enums.EtapaConversaEnum;
import com.guilhermelevi.barbearia.repositories.*;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @Transactional
class AtendimentoHumanoIntegracaoTest {
    @Autowired AtendimentoHumanoService service;
    @Autowired ConversaService conversa;
    @Autowired IAtendimentoHumanoRepository atendimentos;
    @Autowired IMensagemAtendimentoRepository mensagens;
    @Autowired ISessaoConversaRepository sessoes;
    @Autowired EntityManager em;
    @MockitoBean WhatsAppClient whatsapp;
    Barbeiro b, outro; Cliente c;
    @BeforeEach void preparar() {
        b = criar("linha-a"); outro = criar("linha-b");
        c = Cliente.builder().nomeCompleto("Cliente").numeroTelefone("5588991234567").build();em.persist(c);em.flush();
    }
    Barbeiro criar(String linha) {
        var b = Barbeiro.builder().nome("Loja").whatsappPhoneNumberId(linha).numeroWhatsAppNotificacao("5588991111111")
                .inicioExpediente(LocalTime.of(8,0)).fimExpediente(LocalTime.of(18,0)).build();em.persist(b);return b;
    }
    tools.jackson.databind.JsonNode mensagem(String texto) {
        return JsonMapper.builder().build().valueToTree(Map.of("id",UUID.randomUUID().toString(),"type","text",
                "timestamp",String.valueOf(Instant.now().getEpochSecond()),"from",c.getNumeroTelefone(),"text",Map.of("body",texto)));
    }
    AtendimentoHumano solicitar() { assertTrue(service.interceptar(b,c,mensagem("Falar com alguém")));em.flush();return atendimentos.findByBarbeiroIdAndNumeroCliente(b.getId(),c.getNumeroTelefone()).orElseThrow(); }
    @Test void solicitaUmaVezPausaSoClientePreservaEtapaEFinalizaManualmente() {
        var sessao = SessaoConversa.builder().numeroCliente(c.getNumeroTelefone()).barbeiro(b).etapa(EtapaConversaEnum.ESCOLHENDO_DATA).build();em.persist(sessao);
        var a = solicitar();
        assertTrue(service.interceptar(b,c,mensagem("atendente")));
        assertEquals(1,atendimentos.count());
        assertEquals(EtapaConversaEnum.ESCOLHENDO_DATA,sessao.getEtapa());
        verify(whatsapp,times(1)).enviarTextoAposCommit(eq("linha-a"),eq(c.getNumeroTelefone()),contains("Solicitei"));
        assertFalse(service.interceptar(outro,c,mensagem("oi")));
        a.setUltimaMensagemClienteEm(LocalDateTime.now().minusDays(3));
        assertTrue(service.interceptar(b,c,mensagem("oi"))); // Nunca expira a pausa.
        service.assumir(b.getId(),a.getId(),"admin");
        when(whatsapp.enviarTexto(anyString(),anyString(),anyString())).thenReturn("wamid.resposta");
        service.responder(b.getId(),a.getId(),"admin","Posso ajudar?");
        assertEquals(4,mensagens.findTop100ByAtendimentoIdOrderByIdDesc(a.getId()).size());
        service.finalizar(b.getId(),a.getId(),"admin");
        assertFalse(service.interceptar(b,c,mensagem("oi")));
        assertEquals(EtapaConversaEnum.ESCOLHENDO_DATA,sessao.getEtapa());
    }
    @Test void naoRespondeSemAssumirNemForaDaJanelaNemEmOutroTenant() {
        var a=solicitar();
        assertThrows(ResponseStatusException.class,()->service.responder(b.getId(),a.getId(),"admin","Oi"));
        assertThrows(ResponseStatusException.class,()->service.assumir(outro.getId(),a.getId(),"admin"));
        service.assumir(b.getId(),a.getId(),"admin");
        assertThrows(ResponseStatusException.class,()->service.assumir(b.getId(),a.getId(),"outro"));
        a.setUltimaMensagemClienteEm(LocalDateTime.now(ZoneId.of("America/Fortaleza")).minusHours(25));
        assertThrows(ResponseStatusException.class,()->service.responder(b.getId(),a.getId(),"admin","Oi"));
        verify(whatsapp,never()).enviarTexto(anyString(),anyString(),anyString());
    }
    @Test void avisoComTemplateInvalidoFicaVisivelSemPerderPedido() {
        var a=solicitar();
        assertEquals(1,atendimentos.buscarAvisosPendentes(org.springframework.data.domain.PageRequest.of(0,20)).size());
        when(whatsapp.enviarTemplate(anyString(),anyString(),anyString(),anyList())).thenThrow(new IllegalStateException("Template não aprovado"));
        service.avisar(b.getId(),a.getId());em.flush();
        assertNotNull(a.getErroAviso());assertEquals(AtendimentoHumano.Estado.AGUARDANDO,a.getEstado());
        assertTrue(atendimentos.buscarAvisosPendentes(org.springframework.data.domain.PageRequest.of(0,20)).isEmpty());
    }
    @Test void botaoEMensagensPosterioresNaoChegamAoAgendamento() {
        var node=JsonMapper.builder().build().valueToTree(Map.of("type","interactive","timestamp",String.valueOf(Instant.now().getEpochSecond()),
                "interactive",Map.of("button_reply",Map.of("id","FALAR_ATENDENTE"))));
        assertTrue(service.interceptar(b,c,node));
        var payload=JsonMapper.builder().build().valueToTree(Map.of("entry",List.of(Map.of("changes",List.of(Map.of("value",Map.of(
                "metadata",Map.of("phone_number_id","linha-a"),"messages",List.of(mensagem("oi")))))))));
        b.setRoboAtivo(false); // A pausa global não interrompe um atendimento humano já aberto.
        conversa.processar(payload);
        assertEquals(2,mensagens.findTop100ByAtendimentoIdOrderByIdDesc(atendimentos.findByBarbeiroIdAndNumeroCliente(b.getId(),c.getNumeroTelefone()).orElseThrow().getId()).size());
        verify(whatsapp,never()).enviarMenuPrincipal(anyString(),anyString());
    }
}
