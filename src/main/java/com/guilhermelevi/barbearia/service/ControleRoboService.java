package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.domain.exception.OperacaoAdministrativaException;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ControleRoboService {

    private final IBarbeiroRepository barbeiroRepository;
    private final AutorizacaoBarbeiroService autorizacao;

    @Transactional
    public Barbeiro alterar(Long barbeiroId, String remetente, boolean ativo) {
        Barbeiro barbeiro = barbeiroRepository.buscarParaAgendar(barbeiroId)
                .orElseThrow(() -> new OperacaoAdministrativaException(
                        "Barbearia não encontrada."));

        if (!autorizacao.podeAdministrar(barbeiroId, remetente)) {
            throw new OperacaoAdministrativaException(
                    "Esse número não tem permissão para controlar o robô.");
        }

        // A entidade gerenciada é persistida no commit. Repetir a ação é seguro.
        barbeiro.setRoboAtivo(ativo);
        return barbeiro;
    }
}
