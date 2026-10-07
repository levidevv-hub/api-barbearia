package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import com.guilhermelevi.barbearia.repositories.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"app.whatsapp.central.phone-number-id=990000", "app.whatsapp.central.numero=5511977777777"})
@Transactional
class CentralWhatsappIntegracaoTest {
    @Autowired ConversaService conversa;
    @Autowired EntityManager em;
    @Autowired ISessaoCentralWhatsappRepository sessoes;
    @Autowired ISessaoConversaRepository conversas;
    @Autowired IServicoRepository servicos;
    @Autowired IBloqueioDataRepository bloqueios;
    @MockitoBean WhatsAppClient whatsapp;
    private final List<Barbeiro> barbeiros = new ArrayList<>();
    private final List<String> administradores = new ArrayList<>();
    private Barbeiro central;

    @BeforeEach void preparar() {
        central = criar("Central", "5511966666666", "990000");
        central.setRoboAtivo(false);
        for (int i = 1; i <= 5; i++) {
            String telefone = "551198000000" + i;
            administradores.add(telefone);
            barbeiros.add(criar("Barbeiro " + i, telefone, "linha-" + i));
        }
        em.flush();
    }
    private Barbeiro criar(String nome, String admin, String linha) {
        var b = Barbeiro.builder().nome(nome).numeroWhatsAppAdministrador(admin)
                .numeroWhatsAppNotificacao(admin).whatsappPhoneNumberId(linha)
                .inicioExpediente(LocalTime.of(8, 0)).fimExpediente(LocalTime.of(19, 0)).build();
        em.persist(b); return b;
    }
    @Test void cincoAdministradoresPausamSomenteSuaBarbeariaEReativamMesmoComCentralPausada() {
        for (int i = 0; i < 5; i++) {
            var b = barbeiros.get(i); String admin = administradores.get(i);
            texto(admin, "Minha agenda");
            clique(admin, "CENTRAL_" + b.getId() + "_ADMIN_CONFIRMAR_PAUSA_ROBO");
            assertFalse(b.isRoboAtivo());
            for (int j = 0; j < 5; j++) if (i != j) assertTrue(barbeiros.get(j).isRoboAtivo());
            clique(admin, "CENTRAL_" + b.getId() + "_ADMIN_REATIVAR_ROBO");
            assertTrue(b.isRoboAtivo());
            assertEquals(b.getId(), sessoes.findById("990000:" + admin).orElseThrow().getBarbeiroId());
            assertEquals("linha-" + (i + 1), b.getWhatsappPhoneNumberId());
        }
        assertFalse(central.isRoboAtivo());
    }
    @Test void rejeitaSelecaoEAcaoDeOutroBarbeiroMesmoComIdForjado() {
        String admin = administradores.get(0); var outro = barbeiros.get(1);
        texto(admin, "Minha agenda");
        clique(admin, "CENTRAL_SELECIONAR_" + outro.getId());
        clique(admin, "CENTRAL_" + outro.getId() + "_ADMIN_CONFIRMAR_PAUSA_ROBO");
        clique(admin, "ADMIN_CONFIRMAR_PAUSA_ROBO");
        assertTrue(barbeiros.stream().allMatch(Barbeiro::isRoboAtivo));
        assertEquals(barbeiros.get(0).getId(), sessoes.findById("990000:" + admin).orElseThrow().getBarbeiroId());
    }
    @Test void numeroDesconhecidoNaoGanhaSessaoNemAcessoAClientes() {
        texto("5511999999999", "Minha agenda");
        assertTrue(sessoes.findById("990000:5511999999999").isEmpty());
        verify(whatsapp).enviarTextoAposCommit("990000", "5511999999999",
                "Seu número não está cadastrado como administrador de uma barbearia.");
        assertEquals(0L, em.createQuery("select count(c) from Cliente c", Long.class).getSingleResult());
    }
    @Test void administradorDeVariasBarbeariasSelecionaEEvitaBotoesDeContextoAnterior() {
        String admin = administradores.get(0); var a = barbeiros.get(0); var b = barbeiros.get(1);
        b.setNumeroWhatsAppAdministrador(admin); em.flush();
        texto(admin, "Minha agenda");
        verify(whatsapp).enviarSelecaoBarbeariasCentral(eq("990000"), eq(admin), anyList(), eq(0));
        clique(admin, "CENTRAL_SELECIONAR_" + a.getId());
        clique(admin, "CENTRAL_SELECIONAR_" + b.getId());
        clique(admin, "CENTRAL_" + a.getId() + "_ADMIN_CONFIRMAR_PAUSA_ROBO");
        assertTrue(a.isRoboAtivo()); assertTrue(b.isRoboAtivo());
        clique(admin, "CENTRAL_" + b.getId() + "_ADMIN_CONFIRMAR_PAUSA_ROBO");
        assertFalse(b.isRoboAtivo()); assertTrue(a.isRoboAtivo());
        texto(admin, "Trocar barbearia");
        clique(admin, "CENTRAL_" + b.getId() + "_ADMIN_REATIVAR_ROBO");
        assertFalse(b.isRoboAtivo());
        assertNull(sessoes.findById("990000:" + admin).orElseThrow().getBarbeiroId());
    }
    @Test void revogacaoDePermissaoInvalidadaAntesDaAcaoMesmoComSessaoPersistida() {
        String admin = administradores.get(0); var b = barbeiros.get(0);
        texto(admin, "Minha agenda");
        b.setNumeroWhatsAppAdministrador("5511999999999"); em.flush(); em.clear();
        clique(admin, "CENTRAL_" + b.getId() + "_ADMIN_CONFIRMAR_PAUSA_ROBO");
        assertTrue(em.find(Barbeiro.class, b.getId()).isRoboAtivo());
    }
    @Test void cadastroDeServicoViaCentralPertenceSomenteAoBarbeiroDoRemetente() {
        String admin = administradores.get(0); var b = barbeiros.get(0);
        texto(admin, "Minha agenda");
        clique(admin, "CENTRAL_" + b.getId() + "_ADMIN_CADASTRAR_SERVICO");
        texto(admin, "Corte central"); texto(admin, "35,50"); texto(admin, "30"); texto(admin, "confirmar");
        var lista = servicos.findByBarbeiroId(b.getId());
        assertEquals(1, lista.size());
        assertEquals("Corte central", lista.get(0).getNome());
        assertEquals(new BigDecimal("35.50"), lista.get(0).getPreco());
        for (int i = 1; i < 5; i++) assertTrue(servicos.findByBarbeiroId(barbeiros.get(i).getId()).isEmpty());
        em.flush(); em.clear();
        assertEquals(b.getId(), sessoes.findById("990000:" + admin).orElseThrow().getBarbeiroId());
        assertEquals("linha-1", em.find(Barbeiro.class, b.getId()).getWhatsappPhoneNumberId());
    }
    @Test void servicoDeOutroTenantNaoPodeSerDesativadoPorBotaoForjado() {
        String admin = administradores.get(0); var a = barbeiros.get(0); var b = barbeiros.get(1);
        var servico = Servico.builder().nome("Outro").barbeiro(b).ativo(true)
                .preco(BigDecimal.TEN).duracaoMinutos(30).build(); em.persist(servico); em.flush();
        texto(admin, "Minha agenda");
        clique(admin, "CENTRAL_" + a.getId() + "_ADMIN_DESATIVAR_SERVICO_" + servico.getId());
        assertTrue(servico.isAtivo());
    }
    @Test void mensagensDaCentralNaoIniciamAgendamentoNaLinhaDoBarbeiro() {
        conversa.processar(payload("linha-1", "5511977777777", "\"type\":\"text\",\"text\":{\"body\":\"Administrando\"}"));
        conversa.processar(payload("linha-1", "551177777777", "\"type\":\"text\",\"text\":{\"body\":\"Minha agenda\"}"));
        verifyNoInteractions(whatsapp);
        assertEquals(0L, em.createQuery("select count(c) from Cliente c", Long.class).getSingleResult());
        assertTrue(conversas.findByNumeroClienteAndBarbeiroId("5511977777777", barbeiros.get(0).getId()).isEmpty());
    }
    @Test void selecaoRemovidaOuIdMalformadoNaoExecutaAcao() {
        String admin = administradores.get(0); var b = barbeiros.get(0);
        texto(admin, "Minha agenda");
        clique(admin, "CENTRAL_999999_ADMIN_CONFIRMAR_PAUSA_ROBO");
        clique(admin, "CENTRAL_SELECIONAR_invalido");
        clique(admin, "CENTRAL_999999999999999999999999_ADMIN_PAUSAR_ROBO");
        clique(admin, "CENTRAL_PAGINA_-1");
        assertTrue(b.isRoboAtivo());
    }
    @Test void consultaAgendaEPreviaDeBloqueioNaoMisturamReservas() {
        String admin = administradores.get(0); var a = barbeiros.get(0); var b = barbeiros.get(1);
        var cliente = new Cliente(); cliente.setNomeCompleto("Cliente"); cliente.setNumeroTelefone("5511933333333"); em.persist(cliente);
        var sa = Servico.builder().nome("A").barbeiro(a).preco(BigDecimal.TEN).duracaoMinutos(30).build();
        var sb = Servico.builder().nome("B").barbeiro(b).preco(BigDecimal.TEN).duracaoMinutos(30).build();
        em.persist(sa); em.persist(sb);
        var dia = LocalDate.now().plusDays(5);
        var reservaA = new Agendamento(cliente, a, sa, dia.atTime(10, 0));
        var reservaB = new Agendamento(cliente, b, sb, dia.atTime(11, 0));
        em.persist(reservaA); em.persist(reservaB); em.flush();
        texto(admin, "Minha agenda");
        String data = dia.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        texto(admin, "Agenda " + data);
        org.mockito.ArgumentCaptor<List<Agendamento>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(whatsapp).enviarAgendaDoDia(eq("linha-1"), eq(admin), eq(dia), captor.capture());
        assertEquals(1, captor.getValue().size()); assertEquals(a.getId(), captor.getValue().get(0).getBarbeiro().getId());
        texto(admin, "Bloquear " + data);
        var previa = em.createQuery("select p from PreviaBloqueio p where p.barbeiro.id = :id", PreviaBloqueio.class)
                .setParameter("id", a.getId()).getSingleResult();
        assertEquals(1, previa.getIdsAgendamentos().size());
        clique(admin, "CENTRAL_" + b.getId() + "_ADMIN_CONFIRMAR_BLOQUEIO_" + previa.getId());
        assertFalse(previa.isConsumida());
        clique(admin, "CENTRAL_" + a.getId() + "_ADMIN_CONFIRMAR_BLOQUEIO_" + previa.getId());
        assertTrue(previa.isConsumida());
        assertEquals(com.guilhermelevi.barbearia.domain.enums.StatusAgendamentoEnum.CANCELADO, reservaA.getStatus());
        assertEquals(com.guilhermelevi.barbearia.domain.enums.StatusAgendamentoEnum.CONFIRMADO, reservaB.getStatus());
        assertTrue(bloqueios.existsByBarbeiroIdAndData(a.getId(), dia));
        assertFalse(bloqueios.existsByBarbeiroIdAndData(b.getId(), dia));
        var aviso = em.createQuery("select n from NotificacaoPendente n where n.agendamento.id = :id", NotificacaoPendente.class)
                .setParameter("id", reservaA.getId()).getSingleResult();
        assertEquals("linha-1", aviso.getPhoneNumberId());
        texto(admin, "Liberar " + data);
        assertFalse(bloqueios.existsByBarbeiroIdAndData(a.getId(), dia));
        assertEquals(com.guilhermelevi.barbearia.domain.enums.StatusAgendamentoEnum.CANCELADO, reservaA.getStatus());
    }
    private void texto(String admin, String texto) {
        conversa.processar(payload("990000", admin, "\"type\":\"text\",\"text\":{\"body\":\"" + texto + "\"}"));
    }
    private void clique(String admin, String id) {
        conversa.processar(payload("990000", admin, "\"type\":\"interactive\",\"interactive\":{\"button_reply\":{\"id\":\"" + id + "\"}}"));
    }
    private JsonNode payload(String linha, String remetente, String mensagem) {
        return JsonMapper.builder().build().readTree("""
                {"entry":[{"changes":[{"value":{"metadata":{"phone_number_id":"%s"},
                "messages":[{"from":"%s",%s}]}}]}]}
                """.formatted(linha, remetente, mensagem));
    }
}
