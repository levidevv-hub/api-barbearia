package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AutorizacaoBarbeiroService {

    private final IBarbeiroRepository barbeiroRepository;

    @Transactional(readOnly = true)
    public boolean podeAdministrar(
            Long barbeiroId,
            String numeroRemetente
    ) {
        if (barbeiroId == null
                || numeroRemetente == null
                || !numeroRemetente.matches("[1-9][0-9]{7,14}")) {
            return false;
        }

        return barbeiroRepository.findById(barbeiroId)
                .map(barbeiro -> {
                    String administrador =
                            barbeiro.getNumeroWhatsAppAdministrador();

                    return administrador != null
                            && administrador.equals(numeroRemetente);
                })
                .orElse(false);
    }
}