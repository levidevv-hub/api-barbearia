package com.guilhermelevi.barbearia.dto;

import java.net.URI;

public record StatusForwardingSettings(boolean enabled, URI target, String wabaId, String phoneNumberId) {
    public StatusForwardingSettings {
        if (enabled && (target == null || !"https".equals(target.getScheme()) || target.getHost() == null
                || target.getUserInfo() != null || target.getQuery() != null || target.getFragment() != null
                || !"/webhooks/whatsapp/status".equals(target.getPath())
                || !wabaId.matches("[0-9]{1,30}") || !phoneNumberId.matches("[0-9]{1,30}"))) {
            throw new IllegalArgumentException("Configure destino HTTPS, WABA e remetente do encaminhamento.");
        }
    }
}
