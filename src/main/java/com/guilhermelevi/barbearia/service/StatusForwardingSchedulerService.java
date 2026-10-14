package com.guilhermelevi.barbearia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.whatsapp.status-forwarding.enabled", havingValue = "true")
public class StatusForwardingSchedulerService {
    private static final Logger LOG = LoggerFactory.getLogger(StatusForwardingSchedulerService.class);
    private final IStatusForwardingService forwarding;
    public StatusForwardingSchedulerService(IStatusForwardingService forwarding) { this.forwarding = forwarding; }

    @Scheduled(fixedDelayString = "${app.whatsapp.status-forwarding.poll-delay-ms:1000}",
            initialDelayString = "${app.whatsapp.status-forwarding.poll-delay-ms:1000}",
            scheduler = "statusForwardingTaskScheduler")
    public void process() {
        try {
            forwarding.processNext();
        } catch (RuntimeException ex) {
            LOG.warn("Encaminhamento de status interrompido; eventos persistidos aguardam nova tentativa.");
        }
    }
}
