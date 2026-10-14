package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.dto.StatusForwardingResult;

public interface IStatusForwardingClient {
    StatusForwardingResult forward(byte[] body, String signature);
}
