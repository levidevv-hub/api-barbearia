package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.dto.StatusForwardingClaim;
import com.guilhermelevi.barbearia.dto.StatusForwardingResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StatusForwardingRepository implements IStatusForwardingRepository {
    private final JdbcTemplate jdbc;
    public StatusForwardingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void insertIfAbsent(String fingerprint, byte[] body, String signature, String wabaId, String phoneNumberId) {
        jdbc.update("""
                INSERT INTO status_forwarding_events(fingerprint, body, signature, waba_id, phone_number_id)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (fingerprint) DO NOTHING
                """, fingerprint, body, signature, wabaId, phoneNumberId);
    }

    @Override
    public Optional<StatusForwardingClaim> claimNext(String wabaId, String phoneNumberId, UUID token) {
        return jdbc.query("""
                WITH candidate AS (
                    SELECT fingerprint FROM status_forwarding_events
                    WHERE waba_id = ? AND phone_number_id = ? AND state = 'PENDING'
                      AND next_attempt_at <= CURRENT_TIMESTAMP
                      AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP)
                    ORDER BY next_attempt_at, received_at, fingerprint
                    LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE status_forwarding_events e
                SET claim_token = ?, lease_until = CURRENT_TIMESTAMP + INTERVAL '60 seconds',
                    attempts = attempts + 1
                FROM candidate c WHERE e.fingerprint = c.fingerprint
                RETURNING e.fingerprint, e.body, e.signature, e.attempts, e.claim_token
                """, (rs, row) -> new StatusForwardingClaim(rs.getString("fingerprint"), rs.getBytes("body"),
                rs.getString("signature"), rs.getInt("attempts"), rs.getObject("claim_token", UUID.class)),
                wabaId, phoneNumberId, token).stream().findFirst();
    }

    @Override
    public void complete(StatusForwardingClaim claim, String wabaId, String phoneNumberId, StatusForwardingResult result) {
        if (result.delivered()) {
            jdbc.update("""
                    UPDATE status_forwarding_events
                    SET state = 'FORWARDED', body = NULL, signature = NULL, forwarded_at = CURRENT_TIMESTAMP,
                        last_http_status = ?, last_error = NULL, claim_token = NULL, lease_until = NULL
                    WHERE fingerprint = ? AND waba_id = ? AND phone_number_id = ?
                      AND claim_token = ? AND state = 'PENDING'
                    """, result.httpStatus(), claim.fingerprint(), wabaId, phoneNumberId, claim.token());
        } else {
            long delay = Math.min(300L, 5L * (1L << Math.min(Math.max(claim.attempts() - 1, 0), 6)));
            jdbc.update("""
                    UPDATE status_forwarding_events
                    SET next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                        last_http_status = ?, last_error = ?, claim_token = NULL, lease_until = NULL
                    WHERE fingerprint = ? AND waba_id = ? AND phone_number_id = ?
                      AND claim_token = ? AND state = 'PENDING'
                    """, delay, result.httpStatus(), result.reason(), claim.fingerprint(), wabaId, phoneNumberId, claim.token());
        }
    }
}
