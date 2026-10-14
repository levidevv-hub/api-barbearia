package com.guilhermelevi.barbearia.dto;

public record StatusForwardingResult(boolean delivered, Integer httpStatus, String reason) {}
