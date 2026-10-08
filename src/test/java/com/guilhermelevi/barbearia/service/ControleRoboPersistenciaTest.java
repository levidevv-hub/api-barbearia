package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.domain.exception.OperacaoAdministrativaException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ControleRoboPersistenciaTest {
    private static final String ADMIN = "5588912345678";
    @Autowired EntityManager em;
    @Autowired ControleRoboService controle;

    private Barbeiro barbeiro(String nome, String administrador) {
        var b = Barbeiro.builder().nome(nome).numeroWhatsAppAdministrador(administrador).build();
        em.persist(b);
        em.flush();
        return b;
    }

    @Test
    void persistePausaEReativacaoSemAlterarOutraBarbeariaEAcoesRepetidasSaoSeguras() {
        var a = barbeiro("A", ADMIN);
        var b = barbeiro("B", "5588912345679");
        assertTrue(a.isRoboAtivo());
        assertTrue(new Barbeiro().isRoboAtivo());
        controle.alterar(a.getId(), ADMIN, false);
        controle.alterar(a.getId(), ADMIN, false);
        em.flush();
        em.clear();
        assertFalse(em.find(Barbeiro.class, a.getId()).isRoboAtivo());
        assertTrue(em.find(Barbeiro.class, b.getId()).isRoboAtivo());
        controle.alterar(a.getId(), ADMIN, true);
        controle.alterar(a.getId(), ADMIN, true);
        em.flush();
        em.clear();
        assertTrue(em.find(Barbeiro.class, a.getId()).isRoboAtivo());
    }

    @Test
    void rejeitaAdministradorDeOutraBarbeariaSemAlterarEstado() {
        var a = barbeiro("A", ADMIN);
        var b = barbeiro("B", "5588912345679");
        assertThrows(OperacaoAdministrativaException.class,
                () -> controle.alterar(b.getId(), ADMIN, false));
        assertTrue(b.isRoboAtivo());
        assertTrue(a.isRoboAtivo());
    }

    @Test
    void colunaTemDefaultAtivoParaInsercaoSemCampo() {
        em.createNativeQuery("insert into profissionals (nome) values ('Cadastro SQL')").executeUpdate();
        var ativo = em.createNativeQuery("select robo_ativo from barbeiros where nome = 'Cadastro SQL'")
                .getSingleResult();
        assertEquals(Boolean.TRUE, ativo);
    }
}
