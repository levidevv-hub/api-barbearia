package com.guilhermelevi.barbearia.dto;

import java.util.UUID;

public record StatusForwardingClaim(String fingerprint, byte[] body, String signature, int attempts, UUID token) {}
