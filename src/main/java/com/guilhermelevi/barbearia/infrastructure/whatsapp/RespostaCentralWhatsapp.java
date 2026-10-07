package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import java.util.*;

/** Escopo de uma chamada administrativa; capturado antes de agendar o envio após commit. */
public final class RespostaCentralWhatsapp implements AutoCloseable {
    private static final ThreadLocal<RespostaCentralWhatsapp> ATUAL = new ThreadLocal<>();
    private final RespostaCentralWhatsapp anterior;
    private final String phoneNumberId;
    private final Long barbeiroId;

    private RespostaCentralWhatsapp(String phoneNumberId, Long barbeiroId) {
        this.anterior = ATUAL.get();
        this.phoneNumberId = Objects.requireNonNull(phoneNumberId);
        this.barbeiroId = Objects.requireNonNull(barbeiroId);
        ATUAL.set(this);
    }

    public static RespostaCentralWhatsapp abrir(String phoneNumberId, Long barbeiroId) {
        return new RespostaCentralWhatsapp(phoneNumberId, barbeiroId);
    }

    public static Envio preparar(String phoneNumberId, Map<String, Object> body) {
        var contexto = ATUAL.get();
        return contexto == null ? new Envio(phoneNumberId, body)
                : new Envio(contexto.phoneNumberId, contexto.vincular(body));
    }

    private Map<String, Object> vincular(Map<String, Object> original) {
        Map<String, Object> copia = new LinkedHashMap<>();
        original.forEach((chave, valor) -> copia.put(chave, copiar(chave, valor)));
        return Collections.unmodifiableMap(copia);
    }

    private Object copiar(String chave, Object valor) {
        if ("id".equals(chave) && valor instanceof String id && id.startsWith("ADMIN_")) {
            return "CENTRAL_" + barbeiroId + "_" + id;
        }
        if (valor instanceof Map<?, ?> mapa) {
            Map<String, Object> copia = new LinkedHashMap<>();
            mapa.forEach((k, v) -> copia.put((String) k, copiar((String) k, v)));
            return Collections.unmodifiableMap(copia);
        }
        if (valor instanceof List<?> lista) {
            return lista.stream().map(v -> copiar("", v)).toList();
        }
        return valor;
    }

    @Override
    public void close() {
        if (anterior == null) ATUAL.remove(); else ATUAL.set(anterior);
    }

    public record Envio(String phoneNumberId, Map<String, Object> body) {}
}
