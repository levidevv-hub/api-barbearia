package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.dto.StatusForwardingSettings;
import com.guilhermelevi.barbearia.repositories.IStatusForwardingRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Service
public class StatusForwardingInboxService implements IStatusForwardingInboxService {
    private final StatusForwardingSettings settings;
    private final IStatusForwardingRepository repository;
    private final TransactionTemplate transactions;

    public StatusForwardingInboxService(StatusForwardingSettings settings, IStatusForwardingRepository repository,
                                        PlatformTransactionManager manager) {
        this.settings = settings;
        this.repository = repository;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void receive(byte[] body, String signature, JsonNode payload) {
        if (!settings.enabled() || !containsSelectedStatuses(payload)) { return; }
        if (body.length > 1_048_576) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String fingerprint;
        try {
            fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível.");
        }
        // Caller verified the original Meta signature. Commit before bot processing and HTTP 200.
        transactions.executeWithoutResult(status -> repository.insertIfAbsent(fingerprint, body, signature,
                settings.wabaId(), settings.phoneNumberId()));
    }

    private boolean containsSelectedStatuses(JsonNode payload) {
        if (payload == null || !"whatsapp_business_account".equals(payload.path("object").asText())
                || !payload.path("entry").isArray()) { return false; }
        for (JsonNode entry : payload.path("entry")) {
            if (!settings.wabaId().equals(entry.path("id").asText()) || !entry.path("changes").isArray()) { continue; }
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                if ("messages".equals(change.path("field").asText())
                        && "whatsapp".equals(value.path("messaging_product").asText())
                        && settings.phoneNumberId().equals(value.path("metadata").path("phone_number_id").asText())
                        && value.path("statuses").isArray() && !value.path("statuses").isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
}
