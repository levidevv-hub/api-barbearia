package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.dto.*;
import com.guilhermelevi.barbearia.repositories.StatusForwardingRepository;
import com.guilhermelevi.barbearia.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_POSTGRES_URL", matches = ".+")
class StatusForwardingPostgresTest {
    JdbcTemplate admin, jdbc;
    String schema;
    StatusForwardingRepository repository;
    DataSourceTransactionManager manager;
    StatusForwardingSettings settings = new StatusForwardingSettings(true,
            URI.create("https://dispatch.example/webhooks/whatsapp/status"), "123", "456");
    byte[] body = """
            { "object":"whatsapp_business_account", "entry":[{"id":"123","changes":[{
            "field":"messages","value":{"messaging_product":"whatsapp",
            "metadata":{"phone_number_id":"456"},"statuses":[{"id":"wamid.test","status":"read"}]}}]}]}
            """.getBytes(StandardCharsets.UTF_8);
    String signature = "sha256=" + "a".repeat(64);

    @BeforeEach void setup() throws Exception {
        String url = System.getenv("TEST_POSTGRES_URL");
        String user = System.getenv("TEST_POSTGRES_USERNAME"), pass = System.getenv("TEST_POSTGRES_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, pass));
        schema = "forward_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE SCHEMA " + schema);
        var ds = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, user, pass);
        jdbc = new JdbcTemplate(ds);
        manager = new DataSourceTransactionManager(ds);
        jdbc.execute(Files.readString(Path.of("docs/sql/20261014-status-forwarding.sql")));
        repository = new StatusForwardingRepository(jdbc);
    }
    @AfterEach void cleanup() { if (admin != null && schema != null) admin.execute("DROP SCHEMA " + schema + " CASCADE"); }
    void receive() { new StatusForwardingInboxService(settings, repository, manager)
            .receive(body, signature, JsonMapper.builder().build().readTree(body)); }
    String state() { return jdbc.queryForObject("SELECT state FROM status_forwarding_events", String.class); }

    @Test void preservesRawBodyDeduplicatesAndPurgesOnlyAfterSuccess() throws Exception {
        receive(); receive();
        jdbc.execute(Files.readString(Path.of("docs/sql/20261014-status-forwarding.sql")));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM status_forwarding_events", Integer.class));
        var service = new StatusForwardingService(repository, (raw, sig) -> {
            assertArrayEquals(body, raw); assertEquals(signature, sig);
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            jdbc.queryForList("SELECT fingerprint FROM status_forwarding_events FOR UPDATE NOWAIT");
            return new StatusForwardingResult(true, 200, null);
        }, settings, manager);
        assertTrue(service.processNext());
        assertEquals("FORWARDED", state());
        assertNull(jdbc.queryForObject("SELECT body FROM status_forwarding_events", byte[].class));
        assertNull(jdbc.queryForObject("SELECT signature FROM status_forwarding_events", String.class));
        receive(); assertFalse(service.processNext());
    }
    @Test void retriesFailureWithoutLosingPayload() {
        receive();
        var failing = new StatusForwardingService(repository,
                (raw, sig) -> new StatusForwardingResult(false, 503, "HTTP_REJECTED"), settings, manager);
        assertTrue(failing.processNext()); assertEquals("PENDING", state());
        assertFalse(failing.processNext());
        assertArrayEquals(body, jdbc.queryForObject("SELECT body FROM status_forwarding_events", byte[].class));
        jdbc.update("UPDATE status_forwarding_events SET next_attempt_at = CURRENT_TIMESTAMP");
        assertTrue(new StatusForwardingService(repository,
                (raw, sig) -> new StatusForwardingResult(true, 200, null), settings, manager).processNext());
        assertEquals(2, jdbc.queryForObject("SELECT attempts FROM status_forwarding_events", Integer.class));
    }
    @Test void leaseExcludesOtherWorkersAndStaleCompletionCannotOverwrite() {
        receive();
        var old = repository.claimNext("123", "456", UUID.randomUUID()).orElseThrow();
        assertTrue(repository.claimNext("123", "456", UUID.randomUUID()).isEmpty());
        jdbc.update("UPDATE status_forwarding_events SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        var current = repository.claimNext("123", "456", UUID.randomUUID()).orElseThrow();
        repository.complete(old, "123", "456", new StatusForwardingResult(true, 200, null));
        assertEquals("PENDING", state());
        repository.complete(current, "123", "999", new StatusForwardingResult(true, 200, null));
        assertEquals("PENDING", state());
        repository.complete(current, "123", "456", new StatusForwardingResult(true, 200, null));
        assertEquals("FORWARDED", state());
    }
    @Test void ignoresDisabledOtherScopesAndMessagesOnly() {
        var mapper = JsonMapper.builder().build();
        new StatusForwardingInboxService(new StatusForwardingSettings(false, null, "", ""), repository, manager)
                .receive(body, signature, mapper.readTree(body));
        for (String changed : new String[]{new String(body, StandardCharsets.UTF_8).replace("123", "999"),
                new String(body, StandardCharsets.UTF_8).replace("456", "999"),
                new String(body, StandardCharsets.UTF_8).replace("statuses", "messages")}) {
            new StatusForwardingInboxService(settings, repository, manager)
                    .receive(changed.getBytes(StandardCharsets.UTF_8), signature, mapper.readTree(changed));
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM status_forwarding_events", Integer.class));
    }
}
