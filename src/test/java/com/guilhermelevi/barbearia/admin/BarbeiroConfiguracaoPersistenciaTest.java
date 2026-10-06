package com.guilhermelevi.barbearia.admin;

import com.guilhermelevi.barbearia.domain.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.guilhermelevi.barbearia.admin.BarbeiroConfiguracaoController.*;

@SpringBootTest
@Transactional
class BarbeiroConfiguracaoPersistenciaTest {
    @Autowired EntityManager em;
    @Autowired BarbeiroConfiguracaoController controller;
    @Autowired BarbeiroRemocaoService remocao;

    private Barbeiro barbeiro(String nome) {
        var b = new Barbeiro();b.setNome(nome);b.setInicioExpediente(LocalTime.of(8,0));b.setFimExpediente(LocalTime.of(19,0));
        em.persist(b);em.flush();return b;
    }
    private Configuracao configuracao() {
        var semana = Arrays.stream(DayOfWeek.values()).map(d -> new Dia(d,d!=DayOfWeek.SUNDAY,
                d==DayOfWeek.SUNDAY?List.<Periodo>of():List.of(new Periodo(LocalTime.of(8,0),LocalTime.of(12,0)),new Periodo(LocalTime.of(13,30),LocalTime.of(19,0))))).toList();
        return new Configuracao(semana,"Rua Teste, 12",-4.94,-37.97);
    }
    @Test void persisteSemanaLocalizacaoEAtualizaSemColisaoDeChaveUnica() {
        var b=barbeiro("Agenda");var c=configuracao();
        controller.salvar(b.getId(),c);em.clear();
        var salvo=controller.consultar(b.getId());
        assertEquals(7,salvo.semana().size());assertEquals(-4.94,salvo.latitude());
        assertEquals(2,salvo.semana().get(0).periodos().size());assertFalse(salvo.semana().get(6).aberto());
        controller.salvar(b.getId(),c);em.clear();
        assertEquals(2,controller.consultar(b.getId()).semana().get(0).periodos().size());
        controller.salvar(b.getId(),new Configuracao(c.semana(),"Novo endereço",null,null));em.clear();
        assertNull(controller.consultar(b.getId()).longitude());
    }
    @Test void bloqueiaELiberaSomenteDataDoBarbeiroEscolhido() {
        var b=barbeiro("A");var outro=barbeiro("B");var data=LocalDate.now().plusDays(30);
        controller.bloquear(b.getId(),new Bloqueio(data,"Folga"));
        controller.bloquear(outro.getId(),new Bloqueio(data,"Feriado"));
        controller.liberar(b.getId(),new Liberacao(data));
        assertTrue(controller.listarBloqueios(b.getId()).isEmpty());assertEquals(1,controller.listarBloqueios(outro.getId()).size());
    }
    @Test void removeCadastroSemHistoricoELimpaDependenciasSemAfetarOutroBarbeiro() {
        var b=barbeiro("Excluir");var outro=barbeiro("Preservar");
        controller.salvar(b.getId(),configuracao());controller.salvar(outro.getId(),configuracao());
        var servico=new Servico();servico.setNome("Corte");servico.setBarbeiro(b);em.persist(servico);
        var sessao=new SessaoConversa();sessao.setBarbeiro(b);sessao.setServicoSelecionado(servico);em.persist(sessao);
        var conexao=new ConexaoWhatsAppPendente();conexao.setToken(UUID.randomUUID());conexao.setBarbeiro(b);conexao.setExpiraEm(LocalDateTime.now().plusMinutes(30));em.persist(conexao);
        var previa=new PreviaBloqueio(b,"5588999999999",LocalDate.now().plusDays(1),Set.of());em.persist(previa);
        controller.bloquear(b.getId(),new Bloqueio(LocalDate.now().plusDays(20),"Folga"));em.flush();
        remocao.remover(b.getId(),"Excluir");em.clear();
        assertNull(em.find(Barbeiro.class,b.getId()));assertNotNull(em.find(Barbeiro.class,outro.getId()));
        assertNull(em.find(Servico.class,servico.getId()));assertNull(em.find(ConexaoWhatsAppPendente.class,conexao.getId()));
        assertEquals(7,controller.consultar(outro.getId()).semana().size());
    }
    @Test void rejeitaConfirmacaoErradaSemExcluir() {
        var b=barbeiro("Nome exato");
        var erro=assertThrows(ResponseStatusException.class,()->remocao.remover(b.getId(),"outro"));
        assertEquals(400,erro.getStatusCode().value());assertNotNull(em.find(Barbeiro.class,b.getId()));
    }
    @Test void preservaBarbeiroComHistoricoMesmoCancelado() {
        var b=barbeiro("Histórico");
        var cliente=new Cliente();em.persist(cliente);
        var servico=new Servico();servico.setNome("Corte");servico.setBarbeiro(b);servico.setPreco(BigDecimal.TEN);servico.setDuracaoMinutos(30);em.persist(servico);
        var a=new Agendamento(cliente,b,servico,LocalDateTime.now().minusDays(2));a.setStatus(com.guilhermelevi.barbearia.domain.enums.StatusAgendamentoEnum.CANCELADO);em.persist(a);em.flush();
        var erro=assertThrows(ResponseStatusException.class,()->remocao.remover(b.getId(),"Histórico"));
        assertEquals(409,erro.getStatusCode().value());assertNotNull(em.find(Barbeiro.class,b.getId()));assertNotNull(em.find(Agendamento.class,a.getId()));
    }
}
