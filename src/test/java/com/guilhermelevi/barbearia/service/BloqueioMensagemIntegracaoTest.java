package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.admin.BarbeiroConfiguracaoController;
import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.domain.enums.*;
import com.guilhermelevi.barbearia.domain.exception.*;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import com.guilhermelevi.barbearia.repositories.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"app.whatsapp.central.phone-number-id=990000", "app.whatsapp.central.numero=5511977777777"})
@Transactional
class BloqueioMensagemIntegracaoTest {
    @Autowired EntityManager em;
    @Autowired ConversaService conversa;
    @Autowired PreviaBloqueioService previas;
    @Autowired BloqueioDataService bloqueioService;
    @Autowired BarbeiroConfiguracaoController painel;
    @Autowired IBloqueioDataRepository bloqueios;
    @Autowired INotificacaoPendenteRepository notificacoes;
    @Autowired ISessaoConversaRepository sessoes;
    @Autowired EnvioNotificacaoService envio;
    @Autowired AgendamentoService agenda;
    @MockitoBean WhatsAppClient whatsapp;
    Barbeiro barbeiro, outro;
    LocalDate dia = LocalDate.now().plusDays(3);
    final String admin = "5511980000001";

    @BeforeEach void preparar() {
        criarBarbeiro("Central", "5511980000099", "990000");
        barbeiro = criarBarbeiro("A", admin, "linha-a");
        outro = criarBarbeiro("B", "5511980000002", "linha-b");
        when(whatsapp.enviarTexto(anyString(), anyString(), anyString())).thenReturn("wamid.texto");
        when(whatsapp.enviarTemplate(anyString(), anyString(), anyString(), anyList())).thenReturn("wamid.template");
    }
    Barbeiro criarBarbeiro(String nome, String administrador, String linha) {
        var b = Barbeiro.builder().nome(nome).numeroWhatsAppAdministrador(administrador)
                .whatsappPhoneNumberId(linha).inicioExpediente(LocalTime.of(8,0)).fimExpediente(LocalTime.of(18,0)).build();
        em.persist(b); return b;
    }
    Agendamento reservar(Barbeiro b, LocalDate data, int hora, String numero) {
        var c = Cliente.builder().nomeCompleto("Cliente " + numero).numeroTelefone(numero).build();
        var s = Servico.builder().nome("Corte").preco(BigDecimal.TEN).duracaoMinutos(30).barbeiro(b).ativo(true).build();
        em.persist(c); em.persist(s);
        var a = new Agendamento(c,b,s,data.atTime(hora,0)); em.persist(a); em.flush(); return a;
    }
    PreviaBloqueio previaDoBarbeiro() {
        return em.createQuery("select p from PreviaBloqueio p where p.barbeiro.id=:id order by p.expiraEm desc", PreviaBloqueio.class)
                .setParameter("id",barbeiro.getId()).getResultList().get(0);
    }
    void texto(String mensagem) {
        entrada("990000",admin,Map.of("type","text","text",Map.of("body",mensagem)));
    }
    void clique(String acao) {
        entrada("990000",admin,Map.of("type","interactive","interactive",Map.of("button_reply",
                Map.of("id","CENTRAL_"+barbeiro.getId()+"_"+acao))));
    }
    void entrada(String linha, String numero, Map<String,Object> campos) {
        var mensagem = new HashMap<>(campos); mensagem.put("from",numero);
        var value = Map.of("metadata",Map.of("phone_number_id",linha),"messages",List.of(mensagem));
        var payload = Map.of("entry",List.of(Map.of("changes",List.of(Map.of("value",value)))));
        var mapper = JsonMapper.builder().build();
        conversa.processar(mapper.readTree(mapper.writeValueAsString(payload)));
    }
    void consultarBloqueio() {
        texto("Minha agenda"); texto("Bloquear " + dia.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    @Test void mensagemLivrePersisteENotificaTodasAsReservasFuturasSomenteDaDataEDoBarbeiro() {
        var a = reservar(barbeiro,dia,10,"5511911111111");
        var b = reservar(barbeiro,dia,11,"5511911111112");
        var cancelado = reservar(barbeiro,dia,12,"5511911111113"); cancelado.setStatus(StatusAgendamentoEnum.CANCELADO);
        var outraData = reservar(barbeiro,dia.plusDays(1),10,"5511911111114");
        var outroTenant = reservar(outro,dia,10,"5511911111115");
        consultarBloqueio();
        String motivo = "Não atenderei: \"consulta médica\".\nObrigado pela compreensão!";
        texto(motivo);
        assertFalse(bloqueios.existsByBarbeiroIdAndData(barbeiro.getId(),dia));
        UUID id = previaDoBarbeiro().getId(); em.flush(); em.clear();
        assertEquals(motivo,em.find(PreviaBloqueio.class,id).getMotivo());
        clique("ADMIN_CONFIRMAR_BLOQUEIO_"+id); em.flush(); em.clear();
        assertEquals(motivo,bloqueios.findByBarbeiroIdAndData(barbeiro.getId(),dia).orElseThrow().getMotivo());
        assertEquals(StatusAgendamentoEnum.CANCELADO,em.find(Agendamento.class,a.getId()).getStatus());
        assertEquals(StatusAgendamentoEnum.CANCELADO,em.find(Agendamento.class,b.getId()).getStatus());
        assertEquals(StatusAgendamentoEnum.CONFIRMADO,em.find(Agendamento.class,outraData.getId()).getStatus());
        assertEquals(StatusAgendamentoEnum.CONFIRMADO,em.find(Agendamento.class,outroTenant.getId()).getStatus());
        var avisos = notificacoes.findAll(); assertEquals(2,avisos.size());
        for (var aviso : avisos) {
            assertEquals(motivo,aviso.getMotivoBloqueio()); assertTrue(aviso.getMensagem().contains(motivo));
            assertEquals("linha-a",aviso.getPhoneNumberId());
            assertEquals(NotificacaoPendente.Status.PENDENTE,aviso.getStatus());
        }
        clique("ADMIN_CONFIRMAR_BLOQUEIO_"+id); assertEquals(2,notificacoes.count());
    }
    @Test void confirmarSemEscreverMensagemUsaAvisoPadraoETemplateAtual() {
        reservar(barbeiro,dia,10,"5511911111111"); consultarBloqueio();
        clique("ADMIN_CONFIRMAR_BLOQUEIO_"+previaDoBarbeiro().getId());
        assertNull(bloqueios.findByBarbeiroIdAndData(barbeiro.getId(),dia).orElseThrow().getMotivo());
        var aviso = notificacoes.findAll().get(0);
        ReflectionTestUtils.setField(envio,"usarTemplate",true);
        try {
            envio.enviarPendente(aviso.getId());
            verify(whatsapp).enviarTemplate(eq("linha-a"),eq(aviso.getDestinatario()),
                    eq("cancelamento_por_bloqueio_cliente"),argThat(dados->dados.size()==5));
            assertEquals(NotificacaoPendente.Status.ACEITA_PELA_API,aviso.getStatus());
        } finally { ReflectionTestUtils.setField(envio,"usarTemplate",false); }
    }
    @Test void botaoSemMensagemDescartaMotivoAnterior() {
        reservar(barbeiro,dia,10,"5511911111111"); consultarBloqueio(); texto("Imprevisto");
        clique("ADMIN_BLOQUEIO_SEM_MENSAGEM_"+previaDoBarbeiro().getId());
        assertNull(bloqueios.findByBarbeiroIdAndData(barbeiro.getId(),dia).orElseThrow().getMotivo());
        assertNull(notificacoes.findAll().get(0).getMotivoBloqueio());
    }
    @Test void cancelarPreviaNaoBloqueiaNemCancelaENaoReutilizaBotao() {
        var a = reservar(barbeiro,dia,10,"5511911111111"); consultarBloqueio();
        var id = previaDoBarbeiro().getId(); clique("ADMIN_CANCELAR_BLOQUEIO_"+id);
        clique("ADMIN_CONFIRMAR_BLOQUEIO_"+id);
        assertFalse(bloqueios.existsByBarbeiroIdAndData(barbeiro.getId(),dia));
        assertEquals(StatusAgendamentoEnum.CONFIRMADO,a.getStatus()); assertEquals(0,notificacoes.count());
    }
    @Test void mensagemLongaMantemPreviaEMotivoAnteriorSemCancelarNada() {
        consultarBloqueio(); texto("Folga"); texto("x".repeat(256));
        assertEquals("Folga",previaDoBarbeiro().getMotivo());
        assertFalse(previaDoBarbeiro().isConsumida()); assertEquals(0,notificacoes.count());
        assertFalse(bloqueios.existsByBarbeiroIdAndData(barbeiro.getId(),dia));
    }
    @Test void previaExpiradaOuDeOutroAdministradorNaoPermiteMensagemNemConfirmacao() {
        var resultado = previas.criar(barbeiro.getId(),admin,dia);
        assertThrows(OperacaoAdministrativaException.class,
                ()->previas.definirMensagem(barbeiro.getId(),outro.getNumeroWhatsAppAdministrador(),"Inválida"));
        em.createQuery("update PreviaBloqueio p set p.expiraEm=:expira where p.id=:id")
                .setParameter("expira",LocalDateTime.now().minusMinutes(1)).setParameter("id",resultado.id()).executeUpdate();
        em.clear(); assertNull(previas.definirMensagem(barbeiro.getId(),admin,"Atrasada"));
        assertThrows(OperacaoAdministrativaException.class,()->previas.confirmar(resultado.id(),barbeiro.getId(),admin));
        assertEquals(0,bloqueios.count());
    }
    @Test void novosClientesVeemMotivoAoSelecionarDataBloqueadaEConfirmacoesAntigasTambem() {
        var a = reservar(barbeiro,dia,10,"5511911111111");
        var resultado = previas.criar(barbeiro.getId(),admin,dia);
        previas.definirMensagem(barbeiro.getId(),admin,"Não atenderei por manutenção.");
        previas.confirmar(resultado.id(),barbeiro.getId(),admin);
        var c = a.getCliente();
        var sessao = SessaoConversa.builder().barbeiro(barbeiro).numeroCliente(c.getNumeroTelefone())
                .servicoSelecionado(a.getServico()).etapa(EtapaConversaEnum.ESCOLHENDO_DATA).build();
        em.persist(sessao); em.flush();
        entrada("linha-a",c.getNumeroTelefone(),Map.of("type","interactive","interactive",
                Map.of("list_reply",Map.of("id","DATA_"+dia))));
        verify(whatsapp).enviarTextoAposCommit(eq("linha-a"),eq(c.getNumeroTelefone()),
                contains("Não atenderei por manutenção."));
        assertEquals(EtapaConversaEnum.ESCOLHENDO_DATA,sessao.getEtapa()); assertNull(sessao.getDataSelecionada());
        var ex = assertThrows(HorarioIndisponivelException.class,()->agenda.agendar(c,barbeiro,a.getServico(),
                dia.atTime(15,0),BigDecimal.TEN,30));
        assertTrue(ex.getMessage().contains("Não atenderei por manutenção."));
    }
    @Test void templateComMotivoEnviaSeisParametrosEFalhaNaoDesfazCancelamento() {
        var a = reservar(barbeiro,dia,10,"5511911111111"); consultarBloqueio(); texto("Consulta\nmédica.");
        clique("ADMIN_CONFIRMAR_BLOQUEIO_"+previaDoBarbeiro().getId());
        var aviso = notificacoes.findAll().get(0);
        ReflectionTestUtils.setField(envio,"usarTemplate",true);
        try {
            when(whatsapp.enviarTemplate(anyString(),anyString(),anyString(),anyList()))
                    .thenThrow(new IllegalStateException("Template não aprovado"));
            envio.enviarPendente(aviso.getId());
            verify(whatsapp).enviarTemplate(eq("linha-a"),eq(a.getCliente().getNumeroTelefone()),
                    eq("cancelamento_por_bloqueio_cliente_motivo"),argThat(dados->dados.size()==6&&dados.get(5).equals("Consulta médica.")));
            assertEquals(NotificacaoPendente.Status.FALHA,aviso.getStatus());
            assertTrue(aviso.getUltimoErro().contains("Template não aprovado"));
            assertEquals(StatusAgendamentoEnum.CANCELADO,a.getStatus());
        } finally { ReflectionTestUtils.setField(envio,"usarTemplate",false); }
    }
    @Test void painelExigePreviaETambemCancelaNotificaComMensagemOpcional() {
        var a = reservar(barbeiro,dia,10,"5511911111111");
        var b = reservar(outro,dia,10,"5511911111112");
        assertThrows(IllegalArgumentException.class,()->painel.bloquear(barbeiro.getId(),
                new BarbeiroConfiguracaoController.Bloqueio(dia,null)));
        var previa = painel.previaBloqueio(barbeiro.getId(),new BarbeiroConfiguracaoController.Liberacao(dia));
        assertEquals(List.of(a.getId()),previa.stream().map(BloqueioDataService.ReservaAfetada::agendamentoId).toList());
        painel.bloquear(barbeiro.getId(),new BarbeiroConfiguracaoController.Bloqueio(dia,null,List.of(a.getId())));
        assertEquals(StatusAgendamentoEnum.CANCELADO,a.getStatus()); assertEquals(1,notificacoes.count());
        assertEquals(StatusAgendamentoEnum.CONFIRMADO,b.getStatus());
    }
    @Test void reservaNovaDepoisDaPreviaImpedeCancelamentoParcial() {
        var a = reservar(barbeiro,dia,10,"5511911111111");
        var previa = previas.criar(barbeiro.getId(),admin,dia);
        var b = reservar(barbeiro,dia,11,"5511911111112");
        assertThrows(OperacaoAdministrativaException.class,()->previas.confirmar(previa.id(),barbeiro.getId(),admin));
        assertEquals(StatusAgendamentoEnum.CONFIRMADO,a.getStatus());
        assertEquals(StatusAgendamentoEnum.CONFIRMADO,b.getStatus());
        assertEquals(0,notificacoes.count()); assertFalse(bloqueios.existsByBarbeiroIdAndData(barbeiro.getId(),dia));
    }
    @Test void vinteAvisosPausadosNaoImpedemEnvioDeOutraBarbearia() {
        for (int i=0;i<20;i++) notificacoes.save(new NotificacaoPendente(
                reservar(barbeiro,dia,10,"55119111"+String.format("%05d",i)),"Cancelado"));
        barbeiro.setRoboAtivo(false);
        var ativo = notificacoes.save(new NotificacaoPendente(reservar(outro,dia,10,"5511922222222"),"Cancelado"));
        em.flush();
        var prontos = notificacoes.buscarPendentesDeRobosAtivos(NotificacaoPendente.Status.PENDENTE,PageRequest.of(0,20));
        assertEquals(List.of(ativo.getId()),prontos.stream().map(NotificacaoPendente::getId).toList());
    }
}
