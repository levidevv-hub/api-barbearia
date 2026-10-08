package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.admin.*;
import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.domain.exception.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class MultiplosServicosPersistenciaTest {
    @Autowired EntityManager em;
    @Autowired AgendamentoService reservas;
    @Autowired DisponibilidadeService disponibilidade;
    @Autowired ServicoAdminController catalogo;
    @Autowired BarbeiroDadosController dados;
    @Autowired BarbeiroRemocaoService remocao;
    @MockitoBean NotificacaoService notificacao;
    Barbeiro profissional;
    Cliente cliente;
    Servico consulta, retorno;
    LocalDate dia = LocalDate.now().plusDays(14);

    @BeforeEach void preparar() {
        profissional = Barbeiro.builder().nome("Clínica estética").segmento("Estética").build();
        em.persist(profissional);
        cliente = Cliente.builder().nomeCompleto("Cliente").numeroTelefone("5588999000000").build();em.persist(cliente);
        consulta = servico(profissional,"Avaliação",30,"50.00");
        retorno = servico(profissional,"Tratamento",60,"100.00");
        var expediente = ExpedienteSemanal.builder().barbeiro(profissional).diaSemana(dia.getDayOfWeek()).aberto(true).build();
        em.persist(expediente);
        var periodo = PeriodoExpediente.builder().expedienteSemanal(expediente).inicio(LocalTime.of(8,0)).fim(LocalTime.of(12,0)).build();
        em.persist(periodo);em.flush();
    }
    Servico servico(Barbeiro p,String nome,int minutos,String preco) {
        var s=Servico.builder().barbeiro(p).nome(nome).duracaoMinutos(minutos).preco(new BigDecimal(preco)).build();
        em.persist(s);return s;
    }
    List<ItemServico> itens() { return List.of(ItemServico.de(consulta),ItemServico.de(retorno)); }
    Agendamento reservar(List<ItemServico> itens, int hora, int minuto) {
        return reservas.agendarMultiplos(cliente,profissional,itens,dia.atTime(hora,minuto));
    }
    @Test void somaPrecoDuracaoEPreservaSnapshotAposAlterarCatalogo() {
        var reserva=reservar(itens(),9,0);var id=reserva.getId();
        consulta.setNome("Novo nome");consulta.setPreco(BigDecimal.ONE);retorno.setDuracaoMinutos(5);em.flush();em.clear();
        var salvo=em.find(Agendamento.class,id);
        assertEquals(2,salvo.getItens().size());assertEquals("Avaliação + Tratamento",salvo.descricaoServicos());
        assertEquals(new BigDecimal("150.00"),ItemServico.precoTotal(salvo.getItens()));
        assertEquals(dia.atTime(10,30),salvo.calcularFim());
        verify(notificacao).novoAgendamento(any());
    }
    @Test void disponibilidadeUsaSomaENaoUltrapassaExpediente() {
        var horarios=disponibilidade.buscarHorariosPorDuracao(profissional,90,dia);
        assertTrue(horarios.contains(LocalTime.of(10,30)));assertFalse(horarios.contains(LocalTime.of(11,0)));
        assertThrows(HorarioIndisponivelException.class,()->reservar(itens(),11,0));
        verifyNoInteractions(notificacao);
    }
    @Test void impedeReservaQueCruzaIntervalo() {
        var p=em.createQuery("select p from PeriodoExpediente p where p.expedienteSemanal.barbeiro.id = :id",PeriodoExpediente.class)
                .setParameter("id",profissional.getId()).getSingleResult();p.setFim(LocalTime.of(10,0));em.flush();
        assertThrows(HorarioIndisponivelException.class,()->reservar(itens(),9,0));
    }
    @Test void conflitoConsideraUltimoServicoELiberaNoCancelamento() {
        var a=reservar(itens(),9,0);
        assertFalse(disponibilidade.buscarHorariosPorDuracao(profissional,30,dia).contains(LocalTime.of(10,0)));
        assertThrows(HorarioIndisponivelException.class,()->reservar(List.of(ItemServico.de(consulta)),10,0));
        assertTrue(reservas.cancelar(a.getId(),cliente,profissional));
        assertTrue(disponibilidade.buscarHorariosPorDuracao(profissional,30,dia).contains(LocalTime.of(10,0)));
    }
    @Test void validaMudancaIndividualMesmoQuandoTotalPermaneceIgual() {
        var apresentados=itens();consulta.setPreco(new BigDecimal("60.00"));retorno.setPreco(new BigDecimal("90.00"));em.flush();
        assertThrows(ServicoAlteradoException.class,()->reservar(apresentados,9,0));verifyNoInteractions(notificacao);
    }
    @Test void rejeitaDuracaoAlteradaEServicoDesativado() {
        var apresentados=itens();retorno.setDuracaoMinutos(45);em.flush();
        assertThrows(ServicoAlteradoException.class,()->reservar(apresentados,9,0));
        retorno.setAtivo(false);em.flush();
        assertThrows(ServicoIndisponivelException.class,()->reservar(apresentados,9,0));
    }
    @Test void rejeitaItensDeOutroNegocioDuplicadosEVazios() {
        var outro=Barbeiro.builder().nome("Consultoria").build();em.persist(outro);
        var externo=servico(outro,"Orientação",30,"10.00");em.flush();
        assertThrows(ServicoIndisponivelException.class,()->reservar(List.of(ItemServico.de(consulta),ItemServico.de(externo)),9,0));
        assertThrows(ServicoIndisponivelException.class,()->reservar(List.of(ItemServico.de(consulta),ItemServico.de(consulta)),9,0));
        assertThrows(ServicoIndisponivelException.class,()->reservar(List.of(),9,0));
        verifyNoInteractions(notificacao);
    }
    @Test void naoExcluiServicoSecundarioDeReservaOuSelecaoPersistida() {
        var sessao=SessaoConversa.builder().barbeiro(profissional).numeroCliente("123").servicoSelecionado(consulta).build();
        sessao.getItensSelecionados().addAll(itens());em.persist(sessao);em.flush();em.clear();
        assertEquals(2,em.find(SessaoConversa.class,sessao.getId()).getItensSelecionados().size());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->catalogo.remover(profissional.getId(),retorno.getId())).getStatusCode().value());
        em.find(SessaoConversa.class,sessao.getId()).limpar();em.flush();
        reservar(itens(),9,0);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->catalogo.remover(profissional.getId(),retorno.getId())).getStatusCode().value());
    }
    @Test void limpaColecaoAoExcluirCadastroSemHistorico() {
        var sessao=SessaoConversa.builder().barbeiro(profissional).servicoSelecionado(consulta).build();
        sessao.getItensSelecionados().addAll(itens());em.persist(sessao);em.flush();
        remocao.remover(profissional.getId(),profissional.getNome());em.clear();
        assertNull(em.find(SessaoConversa.class,sessao.getId()));
    }
    @Test void preservaAgendamentoLegadoSemItensESegmentoOmitido() {
        var a=new Agendamento(cliente,profissional,consulta,dia.atTime(9,0));em.persist(a);em.flush();em.clear();
        var salvo=em.find(Agendamento.class,a.getId());assertEquals("Avaliação",salvo.descricaoServicos());
        assertEquals(dia.atTime(9,30),salvo.calcularFim());
        dados.salvar(profissional.getId(),new BarbeiroDadosController.Dados("Estética","5588999999999","5588999999999"));
        assertEquals("Estética",dados.consultar(profissional.getId()).segmento());
        dados.salvar(profissional.getId(),new BarbeiroDadosController.Dados("Consultoria","5588999999999","5588999999999","Consultoria"));
        em.clear();assertEquals("Consultoria",dados.consultar(profissional.getId()).segmento());
    }
}
