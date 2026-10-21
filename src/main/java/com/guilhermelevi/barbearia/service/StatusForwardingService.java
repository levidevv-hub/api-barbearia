package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.dto.StatusForwardingResult;
import com.guilhermelevi.barbearia.dto.StatusForwardingSettings;
import com.guilhermelevi.barbearia.repositories.IStatusForwardingRepository;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "app.whatsapp.status-forwarding.enabled", havingValue = "true")
public class StatusForwardingService implements IStatusForwardingService {
    private final IStatusForwardingRepository repository;
    private final IStatusForwardingClient client;
    private final StatusForwardingSettings settings;
    private final TransactionTemplate transactions;

    public StatusForwardingService(IStatusForwardingRepository repository, IStatusForwardingClient client,
                                   StatusForwardingSettings settings, PlatformTransactionManager manager) {
        this.repository = repository;
        this.client = client;
        this.settings = settings;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public boolean processNext() {
        if (!settings.enabled()) { return false; }
        var claim = transactions.execute(status -> repository.claimNext(settings.wabaId(),
                settings.phoneNumberId(), UUID.randomUUID()).orElse(null));
        if (claim == null) { return false; }
        StatusForwardingResult result;
        try {
            // No database transaction held during HTTP.
            result = client.forward(claim.body(), claim.signature());
            if (result == null) { result = new StatusForwardingResult(false, null, "INVALID_CLIENT_RESULT"); }
        } catch (RuntimeException ex) {
            result = new StatusForwardingResult(false, null, "CONNECTION_ERROR");
        }
        StatusForwardingResult outcome = result;
        transactions.executeWithoutResult(status -> repository.complete(claim, settings.wabaId(),
                settings.phoneNumberId(), outcome));
        // Crash before completion: lease expires, exact same event is redelivered.
        return true;
    }
}
