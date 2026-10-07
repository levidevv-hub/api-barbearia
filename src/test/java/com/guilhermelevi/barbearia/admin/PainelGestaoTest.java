package com.guilhermelevi.barbearia.admin;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.domain.enums.StatusAgendamentoEnum;
import com.guilhermelevi.barbearia.service.DisponibilidadeService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static com.guilhermelevi.barbearia.admin.BarbeiroConfiguracaoController.*;

@SpringBootTest
@Transactional
class PainelGestaoTest {
    @Autowired EntityManager em;
    @Autowired ServicoAdminController servicos;
    @Autowired BarbeiroDadosController dados;
    @Autowired BarbeiroConfiguracaoController configuracao;
    @Autowired DisponibilidadeService disponibilidade;
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void setup() { mvc=webAppContextSetup(context).apply(springSecurity()).build(); }
    Barbeiro barbeiro(String nome) {
        var b=new Barbeiro();b.setNome(nome);b.setInicioExpediente(LocalTime.of(8,0));b.setFimExpediente(LocalTime.of(18,0));
        b.setNumeroWhatsAppAdministrador("5588999999999");b.setNumeroWhatsAppNotificacao("5588999999999");
        b.setWhatsappAccessToken("credencial-privada");b.setWhatsappPhoneNumberId("phone-id");
        em.persist(b);em.flush();return b;
    }
    ServicoAdminController.Form form(String nome, int duracao, boolean ativo) {
        return new ServicoAdminController.Form(nome,new BigDecimal("35.50"),duracao,ativo);
    }
    String body() { return "{\"nome\":\"Corte\",\"preco\":35.50,\"duracaoMinutos\":30,\"ativo\":true}"; }
    String url(Barbeiro b) { return "/api/admin/barbeiros/"+b.getId()+"/servicos"; }

    @Test void cadastraEditaDesativaReativaERemoveSemAfetarOutroBarbeiro() {
        var a=barbeiro("A");var b=barbeiro("B");
        var s=servicos.cadastrar(a.getId(),form(" Corte ",30,true));
        servicos.cadastrar(b.getId(),form("Barba",20,true));em.clear();
        assertEquals("Corte",servicos.listar(a.getId()).get(0).nome());
        var editado=servicos.editar(a.getId(),s.id(),form("Corte e barba",60,false));
        assertEquals(60,editado.duracaoMinutos());assertFalse(editado.ativo());
        assertTrue(servicos.status(a.getId(),s.id(),new ServicoAdminController.StatusForm(true)).ativo());
        servicos.remover(a.getId(),s.id());em.clear();
        assertTrue(servicos.listar(a.getId()).isEmpty());assertEquals(1,servicos.listar(b.getId()).size());
    }
    @Test void rejeitaServicoDeOutroBarbeiro() {
        var a=barbeiro("A");var b=barbeiro("B");var s=servicos.cadastrar(a.getId(),form("Corte",30,true));
        var ex=assertThrows(ResponseStatusException.class,()->servicos.editar(b.getId(),s.id(),form("Outro",20,true)));
        assertEquals(404,ex.getStatusCode().value());assertEquals("Corte",servicos.listar(a.getId()).get(0).nome());
    }
    @Test void preservaDuracaoDaReservaEAExclusaoRespeitaHistoricoCancelado() {
        var b=barbeiro("Agenda");var s=servicos.cadastrar(b.getId(),form("Corte",30,true));
        var cliente=new Cliente();em.persist(cliente);
        var a=new Agendamento(cliente,b,em.find(Servico.class,s.id()),LocalDateTime.now().plusDays(20));
        a.setStatus(StatusAgendamentoEnum.CANCELADO);em.persist(a);em.flush();
        servicos.editar(b.getId(),s.id(),form("Corte",60,true));em.flush();em.clear();
        assertEquals(30,em.find(Agendamento.class,a.getId()).getDuracaoMinutos());
        var ex=assertThrows(ResponseStatusException.class,()->servicos.remover(b.getId(),s.id()));
        assertEquals(409,ex.getStatusCode().value());
        assertFalse(servicos.status(b.getId(),s.id(),new ServicoAdminController.StatusForm(false)).ativo());
        assertNotNull(em.find(Agendamento.class,a.getId()));
    }
    @Test void conversaVinculadaImpedeExclusaoMasPermiteDesativar() {
        var b=barbeiro("Agenda");var s=servicos.cadastrar(b.getId(),form("Corte",30,true));
        var sessao=new SessaoConversa();sessao.setBarbeiro(b);sessao.setServicoSelecionado(em.find(Servico.class,s.id()));em.persist(sessao);em.flush();
        assertEquals(409,assertThrows(ResponseStatusException.class,()->servicos.remover(b.getId(),s.id())).getStatusCode().value());
        assertFalse(servicos.status(b.getId(),s.id(),new ServicoAdminController.StatusForm(false)).ativo());
    }
    @Test void conversaComServicoEmEdicaoTambemImpedeExclusao() {
        var b=barbeiro("Agenda");var s=servicos.cadastrar(b.getId(),form("Corte",30,true));
        var sessao=new SessaoConversa();sessao.setBarbeiro(b);sessao.setServicoEmEdicao(em.find(Servico.class,s.id()));em.persist(sessao);em.flush();
        assertEquals(409,assertThrows(ResponseStatusException.class,()->servicos.remover(b.getId(),s.id())).getStatusCode().value());
    }
    @Test void dadosAtualizamSemAlterarCredenciaisOuExpediente() {
        var b=barbeiro("Antes");
        dados.salvar(b.getId(),new BarbeiroDadosController.Dados(" Depois ","5588988888888","5588977777777"));em.clear();
        var salvo=em.find(Barbeiro.class,b.getId());
        assertEquals("Depois",salvo.getNome());assertEquals("5588988888888",salvo.getNumeroWhatsAppAdministrador());
        assertEquals("credencial-privada",salvo.getWhatsappAccessToken());assertEquals("phone-id",salvo.getWhatsappPhoneNumberId());
        assertEquals(LocalTime.of(8,0),salvo.getInicioExpediente());
    }
    @Test void intervaloBloqueiaCorteQueAtravessariaAlmocoESemIntervaloLibera() {
        var b=barbeiro("Agenda");var s=servicos.cadastrar(b.getId(),form("Corte",45,true));
        var data=LocalDate.now().plusDays(10);
        var turnos=List.of(new Periodo(LocalTime.of(8,0),LocalTime.of(12,0)),new Periodo(LocalTime.of(13,30),LocalTime.of(18,0)));
        var semana=Arrays.stream(DayOfWeek.values()).map(d->new Dia(d,true,turnos)).toList();
        configuracao.salvar(b.getId(),new Configuracao(semana,null,null,null));em.flush();em.clear();
        var horarios=disponibilidade.buscarHorarios(em.find(Barbeiro.class,b.getId()),em.find(Servico.class,s.id()),data);
        assertFalse(horarios.contains(LocalTime.of(11,30)));assertFalse(horarios.contains(LocalTime.of(12,0)));assertTrue(horarios.contains(LocalTime.of(13,30)));
        var continuo=Arrays.stream(DayOfWeek.values()).map(d->new Dia(d,true,List.of(new Periodo(LocalTime.of(8,0),LocalTime.of(18,0))))).toList();
        configuracao.salvar(b.getId(),new Configuracao(continuo,null,null,null));em.flush();em.clear();
        assertTrue(disponibilidade.buscarHorarios(em.find(Barbeiro.class,b.getId()),em.find(Servico.class,s.id()),data).contains(LocalTime.of(12,0)));
    }
    @Test void editaDataEMotivoDoBloqueioSemAfetarOutraBarbearia() {
        var a=barbeiro("A");var b=barbeiro("B");var data=LocalDate.now().plusDays(30);
        configuracao.bloquear(a.getId(),new Bloqueio(data,"Folga"));
        configuracao.bloquear(b.getId(),new Bloqueio(data,"Feriado"));
        configuracao.editarBloqueio(a.getId(),new EdicaoBloqueio(data,data.plusDays(1),"Viagem"));em.clear();
        assertEquals(data.plusDays(1),configuracao.listarBloqueios(a.getId()).get(0).data());
        assertEquals("Viagem",configuracao.listarBloqueios(a.getId()).get(0).motivo());
        assertEquals(data,configuracao.listarBloqueios(b.getId()).get(0).data());
    }
    @Test void naoMoveBloqueioParaDataComReserva() {
        var b=barbeiro("Agenda");var data=LocalDate.now().plusDays(30);
        var s=servicos.cadastrar(b.getId(),form("Corte",30,true));var cliente=new Cliente();em.persist(cliente);
        em.persist(new Agendamento(cliente,b,em.find(Servico.class,s.id()),data.plusDays(1).atTime(10,0)));em.flush();
        configuracao.bloquear(b.getId(),new Bloqueio(data,"Folga"));
        assertThrows(IllegalArgumentException.class,()->configuracao.editarBloqueio(b.getId(),new EdicaoBloqueio(data,data.plusDays(1),"Viagem")));
        assertEquals(data,configuracao.listarBloqueios(b.getId()).get(0).data());
    }
    @Test void endpointsExigemAdminECsrf() throws Exception {
        var b=barbeiro("Seguro");
        mvc.perform(get(url(b))).andExpect(status().isUnauthorized());
        mvc.perform(post(url(b)).with(user("admin").roles("ADMIN")).contentType("application/json").content(body())).andExpect(status().isForbidden());
        mvc.perform(post(url(b)).with(user("comum").roles("USER")).with(csrf()).contentType("application/json").content(body())).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/barbeiros/"+b.getId()+"/dados").with(user("admin").roles("ADMIN")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        assertTrue(servicos.listar(b.getId()).isEmpty());
    }
    @Test void validacaoHttpENaoVazamentoDeCredenciais() throws Exception {
        var b=barbeiro("Seguro");
        for(String invalid:List.of(body().replace("35.50","-1"),body().replace("35.50","1.001"),body().replace(":30",":0"),body().replace(":30",":1440"),body().replace("Corte","   "),body().replace(":true",":null"))){
            mvc.perform(post(url(b)).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(url(b)).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nome").value("Corte")).andExpect(jsonPath("$.barbeiro").doesNotExist());
        mvc.perform(get("/api/admin/barbeiros/"+b.getId()+"/dados").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.whatsappAccessToken").doesNotExist());
    }
}
