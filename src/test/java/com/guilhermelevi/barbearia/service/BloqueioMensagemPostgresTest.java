package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_POSTGRES_URL", matches=".+")
class BloqueioMensagemPostgresTest {
    @Test void atualizaTabelasExistentesEPreservaMensagemAposReiniciar() throws Exception {
        String url=System.getenv("TEST_POSTGRES_URL"), usuario=System.getenv("TEST_POSTGRES_USERNAME"), senha=System.getenv("TEST_POSTGRES_PASSWORD");
        String schema="teste_bloqueio_"+UUID.randomUUID().toString().replace("-","");
        try (var conexao=DriverManager.getConnection(url,usuario,senha); var sql=conexao.createStatement()) {
            sql.execute("create schema "+schema);
            try {
                UUID previaId; Long avisoId;
                try (var fabrica=fabrica(url,usuario,senha,schema); var sessao=fabrica.openSession()) {
                    var tx=sessao.beginTransaction();
                    var b=Barbeiro.builder().nome("Barbeiro").whatsappPhoneNumberId("linha").build();
                    var c=Cliente.builder().nomeCompleto("Cliente").numeroTelefone("5511988888888").build();
                    var s=Servico.builder().nome("Corte").preco(BigDecimal.TEN).duracaoMinutos(30).barbeiro(b).build();
                    sessao.persist(b); sessao.persist(c); sessao.persist(s);
                    var a=new Agendamento(c,b,s,LocalDateTime.now().plusDays(2)); sessao.persist(a);
                    var previa=new PreviaBloqueio(b,"5511999999999",a.getInicio().toLocalDate(),Set.of(a.getId()));
                    var aviso=new NotificacaoPendente(a,"Aviso antigo");
                    sessao.persist(previa); sessao.persist(aviso); tx.commit();
                    previaId=previa.getId(); avisoId=aviso.getId();
                }
                // Reproduz as duas tabelas da versão anterior sem os campos novos.
                sql.execute("alter table "+schema+".previas_bloqueio drop column motivo");
                sql.execute("alter table "+schema+".notificacoes_pendentes drop column motivo_bloqueio");
                try (var fabrica=fabrica(url,usuario,senha,schema); var sessao=fabrica.openSession()) {
                    var tx=sessao.beginTransaction();
                    var previa=sessao.find(PreviaBloqueio.class,previaId);
                    var antigo=sessao.find(NotificacaoPendente.class,avisoId);
                    assertNull(previa.getMotivo()); assertNull(antigo.getMotivoBloqueio());
                    assertEquals("Aviso antigo",antigo.getMensagem());
                    previa.definirMotivo("Consulta médica");
                    tx.commit();
                }
                try (var fabrica=fabrica(url,usuario,senha,schema); var sessao=fabrica.openSession()) {
                    assertEquals("Consulta médica",sessao.find(PreviaBloqueio.class,previaId).getMotivo());
                    assertEquals(NotificacaoPendente.Status.PENDENTE,sessao.find(NotificacaoPendente.class,avisoId).getStatus());
                }
            } finally { sql.execute("drop schema "+schema+" cascade"); }
        }
    }
    SessionFactory fabrica(String url,String usuario,String senha,String schema) {
        return new Configuration().addAnnotatedClass(Barbeiro.class).addAnnotatedClass(Cliente.class)
                .addAnnotatedClass(Servico.class).addAnnotatedClass(Agendamento.class)
                .addAnnotatedClass(PreviaBloqueio.class).addAnnotatedClass(NotificacaoPendente.class)
                .setProperty("hibernate.connection.url",url).setProperty("hibernate.connection.username",usuario)
                .setProperty("hibernate.connection.password",senha).setProperty("hibernate.default_schema",schema)
                .setProperty("hibernate.hbm2ddl.auto","update").setProperty("hibernate.hbm2ddl.halt_on_error","true")
                .buildSessionFactory();
    }
}
