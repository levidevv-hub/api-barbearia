package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.domain.enums.EtapaConversaEnum;
import com.guilhermelevi.barbearia.domain.exception.HorarioIndisponivelException;
import com.guilhermelevi.barbearia.infrastructure.whatsapp.WhatsAppClient;
import com.guilhermelevi.barbearia.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversaServiceTest {

    private IBarbeiroRepository barbeiros;
    private ClienteService clientes;
    private IServicoRepository servicos;
    private ISessaoConversaRepository sessoes;
    private DisponibilidadeService disponibilidade;
    private AgendamentoService agendamentos;
    private WhatsAppClient whatsapp;
    private AutorizacaoBarbeiroService autorizacao;
    private BloqueioDataService bloqueios;
    private PreviaBloqueioService previas;
    private ServicoService servicoService;
    private ConversaService conversa;

    private Barbeiro barbeiro;
    private Cliente cliente;
    private Servico servico;
    private SessaoConversa sessao;
    private final LocalDate dia = LocalDate.now().plusDays(2);

    @BeforeEach
    void preparar() {
        barbeiros = mock(IBarbeiroRepository.class);
        clientes = mock(ClienteService.class);
        servicos = mock(IServicoRepository.class);
        sessoes = mock(ISessaoConversaRepository.class);
        disponibilidade = mock(DisponibilidadeService.class);
        agendamentos = mock(AgendamentoService.class);
        whatsapp = mock(WhatsAppClient.class);
        autorizacao = mock(AutorizacaoBarbeiroService.class);
        bloqueios = mock(BloqueioDataService.class);
        previas = mock(PreviaBloqueioService.class);
        servicoService = mock(ServicoService.class);

        ConversaAgendamentoService conversaAgendamento =
                new ConversaAgendamentoService(
                        servicos,
                        sessoes,
                        disponibilidade,
                        agendamentos,
                        whatsapp
                );

        ConversaServicoAdminService conversaServicoAdmin =
                new ConversaServicoAdminService(
                        servicos,
                        sessoes,
                        autorizacao,
                        servicoService,
                        whatsapp
                );

        ConversaAdminService conversaAdmin =
                new ConversaAdminService(
                        autorizacao,
                        bloqueios,
                        previas,
                        agendamentos,
                        conversaServicoAdmin,
                        whatsapp
                );

        conversa = new ConversaService(
                barbeiros,
                clientes,
                conversaAgendamento,
                conversaAdmin
        );

        barbeiro = Barbeiro.builder()
                .id(1L)
                .whatsappPhoneNumberId("api-teste")
                .build();

        cliente = Cliente.builder()
                .id(1L)
                .nomeCompleto("Cliente")
                .numeroTelefone("cliente-teste")
                .build();

        servico = Servico.builder()
                .id(1L)
                .nome("Corte")
                .preco(new BigDecimal("30.00"))
                .duracaoMinutos(30)
                .barbeiro(barbeiro)
                .ativo(true)
                .build();

        sessao = SessaoConversa.builder()
                .id(1L)
                .barbeiro(barbeiro)
                .numeroCliente("cliente-teste")
                .etapa(EtapaConversaEnum.CONFIRMANDO)
                .servicoSelecionado(servico)
                .dataSelecionada(dia)
                .horarioSelecionado(LocalTime.of(10, 0))
                .precoServicoNaConfirmacao(new BigDecimal("30.00"))
                .duracaoServicoNaConfirmacao(30)
                .confirmacaoId(UUID.randomUUID())
                .build();

        when(barbeiros.findByWhatsappPhoneNumberId("api-teste"))
                .thenReturn(Optional.of(barbeiro));
        when(clientes.buscarOuCriar(anyString(), eq("cliente-teste")))
                .thenReturn(cliente);
        when(sessoes.findByNumeroClienteAndBarbeiroId("cliente-teste", 1L))
                .thenReturn(Optional.of(sessao));
    }

    @Test
    void conflitoNaConfirmacaoAtualizaHorarios() {
        when(agendamentos.agendar(
                any(), any(), any(), any(), any(), any()
        )).thenThrow(
                new HorarioIndisponivelException(
                        "Esse horário acabou de ser reservado."
                )
        );

        List<LocalTime> livres = List.of(LocalTime.of(11, 0));
        when(disponibilidade.buscarHorarios(barbeiro, servico, dia))
                .thenReturn(livres);

        conversa.processar(confirmarAtual());

        assertEquals(EtapaConversaEnum.ESCOLHENDO_HORARIO, sessao.getEtapa());
        assertNull(sessao.getHorarioSelecionado());
        verify(whatsapp).enviarTextoAposCommit(
                eq("api-teste"),
                eq("cliente-teste"),
                contains("reservado")
        );
        verify(whatsapp).enviarHorarios(
                "api-teste",
                "cliente-teste",
                livres,
                0
        );
    }

    @Test
    void conflitoSemVagasOfereceOutraData() {
        when(agendamentos.agendar(
                any(), any(), any(), any(), any(), any()
        )).thenThrow(new HorarioIndisponivelException("Horário ocupado."));

        when(disponibilidade.buscarHorarios(barbeiro, servico, dia))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> conversa.processar(confirmarAtual()));

        assertEquals(EtapaConversaEnum.ESCOLHENDO_DATA, sessao.getEtapa());
        assertNull(sessao.getDataSelecionada());
        assertNull(sessao.getHorarioSelecionado());
        verify(whatsapp).enviarDatas("api-teste", "cliente-teste");
    }

    @Test
    void confirmacaoAntigaNaoCriaOutraReserva() {
        sessao.limpar();

        conversa.processar(confirmarAtual());

        verifyNoInteractions(agendamentos);
        verify(whatsapp).enviarTextoAposCommit(
                anyString(),
                anyString(),
                contains("etapa anterior")
        );
    }

    @Test
    void sucessoLimpaSessao() {
        Agendamento salvo = new Agendamento(
                cliente,
                barbeiro,
                servico,
                dia.atTime(10, 0)
        );

        when(agendamentos.agendar(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(salvo);

        conversa.processar(confirmarAtual());

        assertEquals(EtapaConversaEnum.MENU, sessao.getEtapa());
        assertNull(sessao.getServicoSelecionado());
        assertNull(sessao.getPrecoServicoNaConfirmacao());
        verify(whatsapp).enviarTextoAposCommit(
                anyString(),
                anyString(),
                contains("Agendamento confirmado")
        );
    }

    private JsonNode confirmarAtual() {
        return clique("CONFIRMAR_" + sessao.getConfirmacaoId());
    }

    private JsonNode clique(String id) {
        return JsonMapper.builder().build().readTree("""
                {"entry":[{"changes":[{"value":{
                  "metadata":{"phone_number_id":"api-teste"},
                  "contacts":[{"profile":{"name":"Cliente"}}],
                  "messages":[{"from":"cliente-teste","type":"interactive",
                    "interactive":{"button_reply":{"id":"%s"}}}]
                }}]}]}
                """.formatted(id));
    }
}
