package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.domain.enums.EtapaConversaEnum;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import com.guilhermelevi.barbearia.repositories.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MultiplosServicosConversaTest {
    IServicoRepository servicos=mock(IServicoRepository.class);
    ISessaoConversaRepository sessoes=mock(ISessaoConversaRepository.class);
    DisponibilidadeService disponibilidade=mock(DisponibilidadeService.class);
    AgendamentoService agendamento=mock(AgendamentoService.class);
    WhatsAppClient whatsapp=mock(WhatsAppClient.class);
    ConversaAgendamentoService conversa=new ConversaAgendamentoService(servicos,sessoes,disponibilidade,agendamento,whatsapp);
    Barbeiro profissional=Barbeiro.builder().id(1L).whatsappPhoneNumberId("phone").build();
    Cliente cliente=Cliente.builder().id(2L).numeroTelefone("numero").build();
    SessaoConversa sessao=SessaoConversa.builder().barbeiro(profissional).build();
    Servico a=Servico.builder().id(3L).nome("Consulta").preco(BigDecimal.TEN).duracaoMinutos(30).barbeiro(profissional).build();
    Servico b=Servico.builder().id(4L).nome("Procedimento").preco(BigDecimal.TEN).duracaoMinutos(60).barbeiro(profissional).build();
    LocalDate dia=LocalDate.now().plusDays(10);
    @BeforeEach void setup() {
        when(sessoes.findByNumeroClienteAndBarbeiroId("numero",1L)).thenReturn(Optional.of(sessao));
        when(servicos.findByBarbeiroIdAndAtivoTrue(1L)).thenReturn(List.of(a,b));
        when(servicos.findByIdAndBarbeiroIdAndAtivoTrue(3L,1L)).thenReturn(Optional.of(a));
        when(servicos.findByIdAndBarbeiroIdAndAtivoTrue(4L,1L)).thenReturn(Optional.of(b));
    }
    void clicar(String id) { conversa.processarInteracao(id,profissional,cliente,"numero"); }
    void selecionarDois() { clicar("AGENDAR");clicar("SERVICO_3");clicar("ADICIONAR_SERVICO");clicar("SERVICO_4"); }
    @Test void fluxoCompletoComDoisServicosConfirmaUmaVezELimpaSelecao() {
        selecionarDois();assertEquals(EtapaConversaEnum.REVISANDO_SERVICOS,sessao.getEtapa());
        verify(whatsapp,never()).enviarDatas(anyString(),anyString());
        verify(whatsapp).enviarServicos("phone","numero",List.of(b));
        clicar("CONTINUAR_AGENDAMENTO");
        when(disponibilidade.buscarHorariosPorDuracao(profissional,90,dia)).thenReturn(List.of(LocalTime.of(9,0)));
        clicar("DATA_"+dia);clicar("HORA_09:00");
        assertEquals(new BigDecimal("20"),sessao.getPrecoServicoNaConfirmacao());assertEquals(90,sessao.getDuracaoServicoNaConfirmacao());
        var reserva=new Agendamento(cliente,profissional,a,dia.atTime(9,0));reserva.setItens(new ArrayList<>(sessao.getItensSelecionados()));
        when(agendamento.agendarMultiplos(eq(cliente),eq(profissional),anyList(),eq(dia.atTime(9,0)))).thenReturn(reserva);
        String confirmar="CONFIRMAR_"+sessao.getConfirmacaoId();clicar(confirmar);clicar(confirmar);
        verify(agendamento,times(1)).agendarMultiplos(any(),any(),anyList(),any());
        verify(whatsapp).enviarTextoAposCommit(eq("phone"),eq("numero"),contains("Consulta + Procedimento"));
        assertTrue(sessao.getItensSelecionados().isEmpty());assertNull(sessao.getConfirmacaoId());
    }
    @Test void impedeDuplicataEMensagemAntigaEPodeVoltarSemAdicionar() {
        clicar("AGENDAR");clicar("SERVICO_3");clicar("SERVICO_4");assertEquals(1,sessao.getItensSelecionados().size());
        clicar("ADICIONAR_SERVICO");clicar("SERVICO_3");assertEquals(1,sessao.getItensSelecionados().size());
        clicar("REVISAR_SERVICOS");clicar("CONTINUAR_AGENDAMENTO");assertEquals(EtapaConversaEnum.ESCOLHENDO_DATA,sessao.getEtapa());
    }
    @Test void refazerRemoveTodosOsItensEConfirmacaoAnterior() {
        selecionarDois();sessao.setConfirmacaoId(UUID.randomUUID());clicar("REFAZER_SERVICOS");
        assertTrue(sessao.getItensSelecionados().isEmpty());assertNull(sessao.getServicoSelecionado());assertNull(sessao.getConfirmacaoId());
    }
    @Test void catalogoEsgotadoMantemResumoEContinuar() {
        selecionarDois();clicar("ADICIONAR_SERVICO");assertEquals(EtapaConversaEnum.REVISANDO_SERVICOS,sessao.getEtapa());
        clicar("CONTINUAR_AGENDAMENTO");assertEquals(EtapaConversaEnum.ESCOLHENDO_DATA,sessao.getEtapa());
    }
    @Test void servicoRemovidoOuIdMalformadoNaoReserva() {
        clicar("AGENDAR");clicar("SERVICO_invalido");assertTrue(sessao.getItensSelecionados().isEmpty());
        clicar("SERVICO_99");assertEquals(EtapaConversaEnum.MENU,sessao.getEtapa());verifyNoInteractions(agendamento);
    }
}
