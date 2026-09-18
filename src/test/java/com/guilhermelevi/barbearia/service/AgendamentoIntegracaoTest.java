package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.domain.enums.StatusAgendamentoEnum;
import com.guilhermelevi.barbearia.domain.exception.HorarioIndisponivelException;
import com.guilhermelevi.barbearia.repositories.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AgendamentoIntegracaoTest {
    private static AnnotationConfigApplicationContext context;
    private AgendamentoService service;
    private DisponibilidadeService disponibilidade;
    private IAgendamentoRepository agendamentos;
    private IBarbeiroRepository barbeiros;
    private IServicoRepository servicos;
    private IClienteRepository clientes;
    private JdbcTemplate jdbc;
    private Barbeiro barbeiro;
    private Servico servico;
    private Cliente cliente;
    private final LocalDate dia = LocalDate.now().plusDays(2);

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = IAgendamentoRepository.class)
    @Import({AgendamentoService.class, DisponibilidadeService.class})
    static class Config {
        @Bean DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:barbearia;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
            dataSource.setUser("sa");
            return dataSource;
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.guilhermelevi.barbearia.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean NotificacaoService notificacaoService() { return mock(NotificacaoService.class); }
    }

    @BeforeAll static void iniciar() { context = new AnnotationConfigApplicationContext(Config.class); }
    @AfterAll static void encerrar() { if (context != null) context.close(); }

    @BeforeEach void preparar() {
        service = context.getBean(AgendamentoService.class);
        disponibilidade = context.getBean(DisponibilidadeService.class);
        agendamentos = context.getBean(IAgendamentoRepository.class);
        barbeiros = context.getBean(IBarbeiroRepository.class);
        servicos = context.getBean(IServicoRepository.class);
        clientes = context.getBean(IClienteRepository.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        agendamentos.deleteAll();
        servicos.deleteAll();
        clientes.deleteAll();
        barbeiros.deleteAll();
        barbeiro = barbeiros.save(Barbeiro.builder().nome("Teste")
                .whatsappPhoneNumberId("numero-teste")
                .inicioExpediente(LocalTime.of(8, 0)).fimExpediente(LocalTime.of(18, 0)).build());
        servico = servicos.save(Servico.builder().nome("Corte e barba").duracaoMinutos(50)
                .preco(BigDecimal.valueOf(45)).barbeiro(barbeiro).build());
        cliente = clientes.save(new Cliente("Cliente teste", "cliente-teste"));
    }

    @Test void salvaConfirmadoEBloqueiaSobreposicaoParcial() {
        Agendamento a = service.agendar(cliente, barbeiro, servico, dia.atTime(10, 0));
        assertEquals(StatusAgendamentoEnum.CONFIRMADO, agendamentos.findById(a.getId()).orElseThrow().getStatus());
        assertThrows(HorarioIndisponivelException.class,
                () -> service.agendar(cliente, barbeiro, servico, dia.atTime(10, 30)));
        assertEquals(1, agendamentos.count());
        assertFalse(disponibilidade.buscarHorarios(barbeiro, servico, dia).contains(LocalTime.of(10, 30)));
    }

    @Test void permiteComecarNoFimDoAnterior() {
        service.agendar(cliente, barbeiro, servico, dia.atTime(10, 0));
        assertDoesNotThrow(() -> service.agendar(cliente, barbeiro, servico, dia.atTime(10, 50)));
        assertEquals(2, agendamentos.count());
    }

    @Test void reservasLegadasSemStatusTambemOcupamHorario() {
        Agendamento a = service.agendar(cliente, barbeiro, servico, dia.atTime(10, 0));
        jdbc.update("update agendamentos set status = null where id = ?", a.getId());
        assertFalse(disponibilidade.buscarHorarios(barbeiro, servico, dia).contains(LocalTime.of(10, 0)));
        assertThrows(HorarioIndisponivelException.class,
                () -> service.agendar(cliente, barbeiro, servico, dia.atTime(10, 0)));
    }

    @Test void canceladoLiberaHorario() {
        Agendamento a = service.agendar(cliente, barbeiro, servico, dia.atTime(10, 0));
        a.setStatus(StatusAgendamentoEnum.CANCELADO);
        agendamentos.saveAndFlush(a);
        assertTrue(disponibilidade.buscarHorarios(barbeiro, servico, dia).contains(LocalTime.of(10, 0)));
        assertDoesNotThrow(() -> service.agendar(cliente, barbeiro, servico, dia.atTime(10, 0)));
    }

    @Test void barbeirosDiferentesPodemTerMesmoHorario() {
        Barbeiro outro = barbeiros.save(Barbeiro.builder().nome("Outro")
                .whatsappPhoneNumberId("outro-numero").inicioExpediente(LocalTime.of(8, 0))
                .fimExpediente(LocalTime.of(18, 0)).build());
        Servico outroServico = servicos.save(Servico.builder().nome("Corte").duracaoMinutos(50)
                .preco(BigDecimal.TEN).barbeiro(outro).build());
        service.agendar(cliente, barbeiro, servico, dia.atTime(10, 0));
        assertDoesNotThrow(() -> service.agendar(cliente, outro, outroServico, dia.atTime(10, 0)));
    }

    @Test void rejeitaPassadoEFimForaDoExpediente() {
        assertThrows(HorarioIndisponivelException.class,
                () -> service.agendar(cliente, barbeiro, servico, LocalDateTime.now().minusMinutes(1)));
        assertThrows(HorarioIndisponivelException.class,
                () -> service.agendar(cliente, barbeiro, servico, dia.atTime(17, 30)));
        assertEquals(0, agendamentos.count());
        assertTrue(disponibilidade.buscarHorarios(barbeiro, servico, LocalDate.now().minusDays(1)).isEmpty());
    }

    @Test void listaVaziaQuandoServicoNaoCabeNoExpediente() {
        servico.setDuracaoMinutos(601);
        assertTrue(disponibilidade.buscarHorarios(barbeiro, servico, dia).isEmpty());
    }

    @Test void reservaLegadaCruzandoMeiaNoiteBloqueiaInicioDoDia() {
        barbeiro.setInicioExpediente(LocalTime.MIDNIGHT);
        barbeiros.saveAndFlush(barbeiro);
        Agendamento legado = new Agendamento(cliente, barbeiro, servico, dia.minusDays(1).atTime(23, 45));
        agendamentos.saveAndFlush(legado);
        assertFalse(disponibilidade.buscarHorarios(barbeiro, servico, dia).contains(LocalTime.MIDNIGHT));
        assertThrows(HorarioIndisponivelException.class,
                () -> service.agendar(cliente, barbeiro, servico, dia.atStartOfDay()));
    }

    @Test void confirmacoesSimultaneasCriamApenasUmaReserva() throws Exception {
        int tentativas = 6;
        ExecutorService pool = Executors.newFixedThreadPool(tentativas);
        CountDownLatch prontas = new CountDownLatch(tentativas);
        CountDownLatch iniciar = new CountDownLatch(1);
        AtomicInteger sucesso = new AtomicInteger();
        AtomicInteger conflito = new AtomicInteger();
        List<Future<?>> tarefas = new ArrayList<>();
        try {
            for (int i = 0; i < tentativas; i++) {
                tarefas.add(pool.submit(() -> {
                    prontas.countDown();
                    try {
                        if (!iniciar.await(10, TimeUnit.SECONDS)) throw new AssertionError("Início não liberado");
                        service.agendar(cliente, barbeiro, servico, dia.atTime(10, 0));
                        sucesso.incrementAndGet();
                    } catch (HorarioIndisponivelException e) {
                        conflito.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }));
            }
            assertTrue(prontas.await(10, TimeUnit.SECONDS));
            iniciar.countDown();
            for (Future<?> tarefa : tarefas) tarefa.get(20, TimeUnit.SECONDS);
            assertEquals(1, sucesso.get());
            assertEquals(tentativas - 1, conflito.get());
            assertEquals(1, agendamentos.count());
        } finally {
            iniciar.countDown();
            pool.shutdownNow();
        }
    }
}