package com.guilhermelevi.barbearia.domain;

import com.guilhermelevi.barbearia.domain.enums.EtapaConversaEnum;
import com.guilhermelevi.barbearia.domain.enums.StatusAgendamentoEnum;
import com.guilhermelevi.barbearia.domain.exception.OperacaoAdministrativaException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.*;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DomainBehaviorTest {

    @Test
    void agendamentoCopiaDuracaoDoServicoECalculaFim() {
        Servico servico = Servico.builder().duracaoMinutos(45).build();
        LocalDateTime inicio = LocalDateTime.of(2030, 1, 1, 10, 0);

        Agendamento agendamento = new Agendamento(
                Cliente.builder().build(),
                Barbeiro.builder().build(),
                servico,
                inicio
        );

        assertEquals(45, agendamento.getDuracaoMinutos());
        assertEquals(inicio.plusMinutes(45), agendamento.calcularFim());
        assertEquals(StatusAgendamentoEnum.CONFIRMADO, agendamento.getStatus());
    }

    @Test
    void agendamentoRejeitaDuracaoInvalidaAoCalcularFim() {
        Agendamento agendamento = Agendamento.builder()
                .inicio(LocalDateTime.now().plusDays(1))
                .duracaoMinutos(0)
                .build();

        assertThrows(IllegalStateException.class, agendamento::calcularFim);
    }

    @Test
    void mensagemRecebidaRespeitaMaquinaDeEstados() {
        MensagemRecebida mensagem = new MensagemRecebida(
                "phone", "wamid.1", "5588999999999", "{}"
        );

        assertEquals(MensagemRecebida.Status.PENDENTE, mensagem.getStatus());

        mensagem.iniciarProcessamento();
        assertEquals(MensagemRecebida.Status.PROCESSANDO, mensagem.getStatus());

        mensagem.registrarFalha();
        assertEquals(MensagemRecebida.Status.FALHA, mensagem.getStatus());

        mensagem.iniciarProcessamento();
        mensagem.concluirProcessamento();

        assertEquals(MensagemRecebida.Status.PROCESSADA, mensagem.getStatus());
        assertNotNull(mensagem.getProcessadaEm());
        assertThrows(IllegalStateException.class, mensagem::iniciarProcessamento);
    }

    @Test
    void notificacaoPendenteControlaTentativasAceiteFalhaEEntrega() {
        Agendamento agendamento = agendamentoCompleto();
        NotificacaoPendente notificacao =
                new NotificacaoPendente(agendamento, "Mensagem");

        assertEquals(NotificacaoPendente.Status.PENDENTE, notificacao.getStatus());
        assertEquals("5588999999999", notificacao.getDestinatario());
        assertEquals("phone-id", notificacao.getPhoneNumberId());

        notificacao.registrarTentativa();
        assertEquals(1, notificacao.getTentativas());
        assertNotNull(notificacao.getUltimaTentativaEm());

        notificacao.registrarAceite("wamid.2");
        assertEquals(NotificacaoPendente.Status.ACEITA_PELA_API, notificacao.getStatus());
        assertEquals("wamid.2", notificacao.getMensagemIdMeta());

        notificacao.registrarFalha("erro");
        assertEquals(NotificacaoPendente.Status.FALHA, notificacao.getStatus());
        assertEquals("erro", notificacao.getUltimoErro());

        notificacao.registrarEntrega();
        assertEquals(NotificacaoPendente.Status.ENTREGUE, notificacao.getStatus());
        assertNull(notificacao.getUltimoErro());

        notificacao.registrarFalha("não deve regredir");
        assertEquals(NotificacaoPendente.Status.ENTREGUE, notificacao.getStatus());
    }

    @Test
    void notificacaoPendenteExigeIdNoAceite() {
        NotificacaoPendente notificacao =
                new NotificacaoPendente(agendamentoCompleto(), "Mensagem");

        assertThrows(
                IllegalArgumentException.class,
                () -> notificacao.registrarAceite(" ")
        );
    }

    @Test
    void periodoAgendamentoValidaIntervaloESobreposicao() {
        LocalDateTime dez = LocalDateTime.of(2030, 1, 1, 10, 0);
        PeriodoAgendamento a = new PeriodoAgendamento(dez, dez.plusHours(1));
        PeriodoAgendamento b = new PeriodoAgendamento(
                dez.plusMinutes(30), dez.plusHours(2)
        );
        PeriodoAgendamento encostado = new PeriodoAgendamento(
                dez.plusHours(1), dez.plusHours(2)
        );

        assertTrue(a.sobrepoe(b));
        assertFalse(a.sobrepoe(encostado));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PeriodoAgendamento(dez, dez)
        );
        assertThrows(
                NullPointerException.class,
                () -> new PeriodoAgendamento(null, dez)
        );
    }

    @Test
    void previaBloqueioCopiaIdsEImpedeConfirmacaoDepoisDeConsumida() {
        Set<Long> ids = new java.util.HashSet<>(Set.of(1L, 2L));
        PreviaBloqueio previa = new PreviaBloqueio(
                Barbeiro.builder().id(10L).build(),
                "5588999999999",
                LocalDate.now().plusDays(2),
                ids
        );

        ids.add(3L);

        assertEquals(Set.of(1L, 2L), previa.getIdsAgendamentos());
        assertThrows(
                UnsupportedOperationException.class,
                () -> previa.getIdsAgendamentos().add(9L)
        );

        previa.validarParaConfirmar();
        previa.consumir();

        assertThrows(
                OperacaoAdministrativaException.class,
                previa::validarParaConfirmar
        );
    }

    @Test
    void sessaoLimparRemoveEstadoTemporarioEMantemIdentidade() {
        SessaoConversa sessao = SessaoConversa.builder()
                .numeroCliente("5588999999999")
                .etapa(EtapaConversaEnum.CONFIRMANDO)
                .servicoSelecionado(Servico.builder().id(1L).build())
                .dataSelecionada(LocalDate.now().plusDays(1))
                .horarioSelecionado(LocalTime.NOON)
                .nomeServicoEmCadastro("Corte")
                .precoServicoEmCadastro(BigDecimal.TEN)
                .duracaoServicoEmCadastro(30)
                .precoServicoNaConfirmacao(BigDecimal.TEN)
                .duracaoServicoNaConfirmacao(30)
                .build();

        sessao.limpar();

        assertEquals(EtapaConversaEnum.MENU, sessao.getEtapa());
        assertNull(sessao.getServicoSelecionado());
        assertNull(sessao.getDataSelecionada());
        assertNull(sessao.getHorarioSelecionado());
        assertNull(sessao.getNomeServicoEmCadastro());
        assertNull(sessao.getPrecoServicoNaConfirmacao());
        assertEquals("5588999999999", sessao.getNumeroCliente());
    }

    @Test
    void statusMensagemRecebidoValidaCamposEMarcaProcessado() {
        StatusMensagemRecebido status = new StatusMensagemRecebido(
                "wamid.3", "phone-id", "delivered", null
        );

        assertFalse(status.isProcessado());
        assertNotNull(status.getRecebidoEm());

        status.marcarProcessado();
        assertTrue(status.isProcessado());

        assertThrows(
                IllegalArgumentException.class,
                () -> new StatusMensagemRecebido("", "phone", "read", null)
        );
    }

    @Test
    void entidadesSimplesPreservamAssociacoesEDefaults() {
        Barbeiro barbeiro = Barbeiro.builder()
                .id(1L)
                .nome("Zalura")
                .whatsappPhoneNumberId("phone-id")
                .build();

        Cliente cliente = new Cliente("Cliente", "5588999999999");
        Servico servico = Servico.builder()
                .id(2L)
                .nome("Corte")
                .preco(new BigDecimal("30.00"))
                .duracaoMinutos(30)
                .barbeiro(barbeiro)
                .build();

        BloqueioData bloqueio = BloqueioData.builder()
                .barbeiro(barbeiro)
                .data(LocalDate.now().plusDays(1))
                .motivo("Folga")
                .build();

        assertEquals("Cliente", cliente.getNomeCompleto());
        assertTrue(servico.isAtivo());
        assertSame(barbeiro, servico.getBarbeiro());
        assertSame(barbeiro, bloqueio.getBarbeiro());
    }

    @Test
    void expedienteSemanalValidaDiaEIntervalo() {
        ExpedienteSemanal semDia = ExpedienteSemanal.builder()
                .aberto(false)
                .build();

        assertInstanceOf(
                IllegalArgumentException.class,
                invocarCallback(semDia, "validarExpediente")
        );

        ExpedienteSemanal abertoInvalido = ExpedienteSemanal.builder()
                .diaSemana(DayOfWeek.MONDAY)
                .aberto(true)
                .inicio(LocalTime.of(10, 0))
                .fim(LocalTime.of(9, 0))
                .build();

        assertInstanceOf(
                IllegalArgumentException.class,
                invocarCallback(abertoInvalido, "validarExpediente")
        );

        ExpedienteSemanal valido = ExpedienteSemanal.builder()
                .diaSemana(DayOfWeek.MONDAY)
                .aberto(true)
                .inicio(LocalTime.of(8, 0))
                .fim(LocalTime.of(18, 0))
                .build();

        assertNull(invocarCallback(valido, "validarExpediente"));
    }

    @Test
    void periodoExpedienteValidaIntervalo() {
        PeriodoExpediente invalido = PeriodoExpediente.builder()
                .inicio(LocalTime.of(10, 0))
                .fim(LocalTime.of(9, 0))
                .build();

        assertInstanceOf(
                IllegalArgumentException.class,
                invocarCallback(invalido, "validarPeriodo")
        );

        PeriodoExpediente valido = PeriodoExpediente.builder()
                .inicio(LocalTime.of(8, 0))
                .fim(LocalTime.of(12, 0))
                .build();

        assertNull(invocarCallback(valido, "validarPeriodo"));
    }

    private static Throwable invocarCallback(Object alvo, String nome) {
        try {
            Method metodo = alvo.getClass().getDeclaredMethod(nome);
            metodo.setAccessible(true);
            metodo.invoke(alvo);
            return null;
        } catch (InvocationTargetException e) {
            return e.getCause();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Agendamento agendamentoCompleto() {
        Cliente cliente = Cliente.builder()
                .nomeCompleto("Cliente")
                .numeroTelefone("5588999999999")
                .build();

        Barbeiro barbeiro = Barbeiro.builder()
                .nome("Barbeiro")
                .whatsappPhoneNumberId("phone-id")
                .build();

        Servico servico = Servico.builder()
                .nome("Corte")
                .duracaoMinutos(30)
                .barbeiro(barbeiro)
                .build();

        return new Agendamento(
                cliente,
                barbeiro,
                servico,
                LocalDateTime.now().plusDays(1)
        );
    }
}
