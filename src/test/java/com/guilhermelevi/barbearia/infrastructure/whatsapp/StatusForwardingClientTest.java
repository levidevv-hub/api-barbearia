package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.dto.StatusForwardingSettings;
import com.guilhermelevi.barbearia.service.StatusForwardingClientService;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import static org.junit.jupiter.api.Assertions.*;

class StatusForwardingClientTest {
    @Test void preservesBytesSignatureAndNeverFollowsRedirects() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] body = "{ \"entry\": [] }\n".getBytes(StandardCharsets.UTF_8);
        var response = new AtomicInteger(200);
        var redirected = new AtomicInteger();
        server.createContext("/target", exchange -> {
            assertArrayEquals(body, exchange.getRequestBody().readAllBytes());
            assertEquals("sha256=test", exchange.getRequestHeaders().getFirst("X-Hub-Signature-256"));
            exchange.getResponseHeaders().add("Location", "/redirected");
            exchange.sendResponseHeaders(response.get(), -1); exchange.close();
        });
        server.createContext("/redirected", exchange -> { redirected.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.start();
        try {
            var client = new StatusForwardingClientService(HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER).build(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/target"));
            assertTrue(client.forward(body, "sha256=test").delivered());
            for (int code : new int[]{302, 403, 503, 202}) {
                response.set(code);
                var result = client.forward(body, "sha256=test");
                assertFalse(result.delivered()); assertEquals(code, result.httpStatus());
            }
            assertEquals(0, redirected.get());
        } finally { server.stop(0); }
    }
    @Test void configurationIsOptInAndSeparatesBotScheduler() {
        var runner = new ApplicationContextRunner().withUserConfiguration(StatusForwardingConfig.class);
        runner.run(context -> {
            assertFalse(context.getBean(StatusForwardingSettings.class).enabled());
            assertFalse(context.containsBean("statusForwardingTaskScheduler"));
        });
        runner.withPropertyValues("app.whatsapp.status-forwarding.enabled=true",
                "app.whatsapp.status-forwarding.url=https://dispatch.example/webhooks/whatsapp/status",
                "app.whatsapp.status-forwarding.waba-id=123", "app.whatsapp.status-forwarding.phone-number-id=456")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotSame(context.getBean("taskScheduler", ThreadPoolTaskScheduler.class),
                            context.getBean("statusForwardingTaskScheduler", ThreadPoolTaskScheduler.class));
                });
        runner.withPropertyValues("app.whatsapp.status-forwarding.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
}
