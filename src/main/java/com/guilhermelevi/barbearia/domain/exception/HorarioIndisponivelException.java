package com.guilhermelevi.barbearia.domain.exception;

public class HorarioIndisponivelException extends IllegalStateException {

    public HorarioIndisponivelException(String mensagem) {
        super(mensagem);
    }
}