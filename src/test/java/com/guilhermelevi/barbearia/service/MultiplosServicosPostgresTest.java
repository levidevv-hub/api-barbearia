package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.domain.enums.EtapaConversaEnum;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_POSTGRES_URL",matches=".+")
class MultiplosServicosPostgresTest {
    @Test void migraSchemaAntigoDuasVezesEPreservaReservasESessoesAposReiniciar() throws Exception {
        String url=System.getenv("TEST_POSTGRES_URL"),user=System.getenv("TEST_POSTGRES_USERNAME"),pass=System.getenv("TEST_POSTGRES_PASSWORD");
        String schema="teste_multi_"+UUID.randomUUID().toString().replace("-","");
        try(var conn=DriverManager.getConnection(url,user,pass);var sql=conn.createStatement()) {
            sql.execute("create schema "+schema);
            sql.execute("set search_path to "+schema);
            try {
                Long reservaId,sessaoId;
                try(var factory=factory(url,user,pass,schema);var session=factory.openSession()) {
                    var tx=session.beginTransaction();
                    var b=Barbeiro.builder().nome("Existente").build();session.persist(b);
                    var c=Cliente.builder().nomeCompleto("Cliente").numeroTelefone("5588999999999").build();session.persist(c);
                    var s=Servico.builder().barbeiro(b).nome("Serviço antigo").preco(BigDecimal.TEN).duracaoMinutos(30).build();session.persist(s);
                    var a=new Agendamento(c,b,s,LocalDateTime.now().plusDays(2));session.persist(a);
                    var conversa=SessaoConversa.builder().barbeiro(b).numeroCliente(c.getNumeroTelefone()).servicoSelecionado(s).etapa(EtapaConversaEnum.ESCOLHENDO_DATA).build();
                    session.persist(conversa);tx.commit();reservaId=a.getId();sessaoId=conversa.getId();
                }
                // Reproduz o schema anterior, inclusive uma restrição que não aceita a etapa nova.
                sql.execute("drop table agendamento_itens");sql.execute("drop table sessao_servicos");
                sql.execute("alter table barbeiros drop column segmento");
                sql.execute("alter table sessoes_conversa add constraint etapa_antiga check (etapa <> 'REVISANDO_SERVICOS')");
                String migration=Files.readString(Path.of("docs/sql/20261008-multiplos-servicos.sql"));
                sql.execute(migration);sql.execute(migration);
                try(var factory=factory(url,user,pass,schema);var session=factory.openSession()) {
                    var tx=session.beginTransaction();
                    var a=session.find(Agendamento.class,reservaId);
                    assertEquals("Serviço antigo",a.descricaoServicos());assertEquals(30,a.getDuracaoMinutos());assertTrue(a.getItens().isEmpty());
                    var conversa=session.find(SessaoConversa.class,sessaoId);
                    assertEquals(EtapaConversaEnum.ESCOLHENDO_DATA,conversa.getEtapa());
                    conversa.setEtapa(EtapaConversaEnum.REVISANDO_SERVICOS);
                    conversa.getItensSelecionados().add(ItemServico.de(conversa.getServicoSelecionado()));
                    a.getItens().add(ItemServico.de(a.getServico()));a.getBarbeiro().setSegmento("Consultoria");tx.commit();
                }
                try(var factory=factory(url,user,pass,schema);var session=factory.openSession()) {
                    assertEquals(1,session.find(Agendamento.class,reservaId).getItens().size());
                    var conversa=session.find(SessaoConversa.class,sessaoId);
                    assertEquals(EtapaConversaEnum.REVISANDO_SERVICOS,conversa.getEtapa());
                    assertEquals("Consultoria",conversa.getBarbeiro().getSegmento());assertEquals(1,conversa.getItensSelecionados().size());
                }
            } finally {sql.execute("drop schema "+schema+" cascade");}
        }
    }
    SessionFactory factory(String url,String user,String pass,String schema) {
        return new Configuration().addAnnotatedClass(Barbeiro.class).addAnnotatedClass(Cliente.class)
                .addAnnotatedClass(Servico.class).addAnnotatedClass(Agendamento.class).addAnnotatedClass(SessaoConversa.class)
                .setProperty("hibernate.connection.url",url).setProperty("hibernate.connection.username",user)
                .setProperty("hibernate.connection.password",pass).setProperty("hibernate.default_schema",schema)
                .setProperty("hibernate.hbm2ddl.auto","update").setProperty("hibernate.hbm2ddl.halt_on_error","true")
                .buildSessionFactory();
    }
}
