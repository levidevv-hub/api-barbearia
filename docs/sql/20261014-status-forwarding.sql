-- Aplicar antes de habilitar STATUS_FORWARDING_ENABLED. Aditiva e idempotente.
BEGIN;
CREATE TABLE IF NOT EXISTS status_forwarding_events (
    fingerprint VARCHAR(64) PRIMARY KEY,
    body BYTEA,
    signature VARCHAR(71),
    waba_id VARCHAR(30) NOT NULL,
    phone_number_id VARCHAR(30) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING', 'FORWARDED')),
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0,
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    last_http_status INTEGER,
    last_error VARCHAR(40),
    forwarded_at TIMESTAMPTZ,
    CONSTRAINT status_forwarding_payload_check
        CHECK (state <> 'PENDING' OR (body IS NOT NULL AND signature IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_status_forwarding_pending
    ON status_forwarding_events(waba_id, phone_number_id, next_attempt_at, received_at)
    WHERE state = 'PENDING';
COMMIT;
