package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.dto.StatusForwardingSettings;
import com.guilhermelevi.barbearia.service.IStatusForwardingClient;
import com.guilhermelevi.barbearia.service.StatusForwardingClientService;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class StatusForwardingConfig {
    // Unqualified bot jobs keep their own scheduler when forwarding is enabled.
    @Bean
    @ConditionalOnProperty(name = "app.whatsapp.status-forwarding.enabled", havingValue = "true")
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("bot-scheduling-");
        return scheduler;
    }

    @Bean
    public StatusForwardingSettings statusForwardingSettings(
            @Value("${app.whatsapp.status-forwarding.enabled:false}") boolean enabled,
            @Value("${app.whatsapp.status-forwarding.url:}") String url,
            @Value("${app.whatsapp.status-forwarding.waba-id:}") String waba,
            @Value("${app.whatsapp.status-forwarding.phone-number-id:}") String phone) {
        try {
            return new StatusForwardingSettings(enabled, enabled ? URI.create(url) : null, waba, phone);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Encaminhamento exige URL HTTPS, WABA e remetente válidos.");
        }
    }

    @Bean
    @ConditionalOnProperty(name = "app.whatsapp.status-forwarding.enabled", havingValue = "true")
    public IStatusForwardingClient statusForwardingClient(StatusForwardingSettings settings) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return new StatusForwardingClientService(http, settings.target());
    }

    @Bean
    @ConditionalOnProperty(name = "app.whatsapp.status-forwarding.enabled", havingValue = "true")
    public ThreadPoolTaskScheduler statusForwardingTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("status-forwarding-");
        scheduler.setDaemon(true);
        return scheduler;
    }
}
