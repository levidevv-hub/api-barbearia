package com.guilhermelevi.barbearia.domain;

import com.guilhermelevi.barbearia.domain.exception.OperacaoAdministrativaException;

public final class MensagemBloqueio {
    private MensagemBloqueio() {}

    public static String normalizar(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) return null;
        String texto = mensagem.strip();
        if (texto.length() > 255) {
            throw new OperacaoAdministrativaException("A mensagem deve ter até 255 caracteres.");
        }
        return texto;
    }

    public static String indisponibilidade(String motivo) {
        String mensagem = normalizar(motivo);
        return "O estabelecimento não atenderá nessa data."
                + (mensagem == null ? "" : "\n\nMensagem do estabelecimento: " + mensagem)
                + "\n\nEscolha outro dia.";
    }
}
