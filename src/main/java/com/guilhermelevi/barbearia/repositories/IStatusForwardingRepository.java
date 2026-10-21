package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.dto.StatusForwardingClaim;
import com.guilhermelevi.barbearia.dto.StatusForwardingResult;
import java.util.Optional;
import java.util.UUID;

public interface IStatusForwardingRepository {
    void insertIfAbsent(String fingerprint, byte[] body, String signature, String wabaId, String phoneNumberId);
    Optional<StatusForwardingClaim> claimNext(String wabaId, String phoneNumberId, UUID token);
    void complete(StatusForwardingClaim claim, String wabaId, String phoneNumberId, StatusForwardingResult result);
}
