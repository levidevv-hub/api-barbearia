package com.guilhermelevi.barbearia.domain.exception;

public class OperacaoAdministrativaException extends RuntimeException {

    public OperacaoAdministrativaException(String mensagem) {
        super(mensagem);
    }
}