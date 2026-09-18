package com.guilhermelevi.barbearia.domain.exception;

public class ServicoAlteradoException extends RuntimeException {

    public ServicoAlteradoException(String mensagem) {
        super(mensagem);
    }
}