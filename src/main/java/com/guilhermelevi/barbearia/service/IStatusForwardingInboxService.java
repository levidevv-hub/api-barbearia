package com.guilhermelevi.barbearia.service;

import tools.jackson.databind.JsonNode;

public interface IStatusForwardingInboxService {
    void receive(byte[] body, String signature, JsonNode payload);
}
