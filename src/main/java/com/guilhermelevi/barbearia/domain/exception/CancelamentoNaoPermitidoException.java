package com.guilhermelevi.barbearia.domain.exception;

public class CancelamentoNaoPermitidoException extends IllegalStateException {

    public CancelamentoNaoPermitidoException(String mensagem) {
        super(mensagem);
    }
}