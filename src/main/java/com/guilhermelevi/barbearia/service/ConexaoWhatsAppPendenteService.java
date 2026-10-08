package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.domain.ConexaoWhatsAppPendente;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import com.guilhermelevi.barbearia.repositories.IConexaoWhatsAppPendenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ConexaoWhatsAppPendenteService {

    private final IBarbeiroRepository barbeiroRepository;
    private final IConexaoWhatsAppPendenteRepository conexaoRepository;

    public ConexaoWhatsAppPendenteService(
            IBarbeiroRepository barbeiroRepository,
            IConexaoWhatsAppPendenteRepository conexaoRepository
    ) {
        this.barbeiroRepository = barbeiroRepository;
        this.conexaoRepository = conexaoRepository;
    }

    @Transactional
    public UUID gerarToken(Long barbeiroId) {

        Barbeiro barbeiro = barbeiroRepository
                .findById(barbeiroId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Profissional não encontrado."
                        )
                );

        UUID token = UUID.randomUUID();

        ConexaoWhatsAppPendente conexao =
                ConexaoWhatsAppPendente.builder()
                        .token(token)
                        .barbeiro(barbeiro)
                        .expiraEm(
                                LocalDateTime.now()
                                        .plusMinutes(30)
                        )
                        .usado(false)
                        .build();

        conexaoRepository.save(conexao);

        return token;
    }

    @Transactional(readOnly = true)
    public ConexaoWhatsAppPendente buscarValida(UUID token) {

        if (token == null) {
            throw new IllegalArgumentException(
                    "Token de conexão não informado."
            );
        }

        ConexaoWhatsAppPendente conexao = conexaoRepository
                .findByToken(token)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Token de conexão inválido."
                        )
                );

        if (conexao.isUsado()) {
            throw new IllegalArgumentException(
                    "Este link de conexão já foi utilizado."
            );
        }

        if (conexao.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Este link de conexão expirou."
            );
        }

        return conexao;
    }

    @Transactional
    public void marcarComoUsada(ConexaoWhatsAppPendente conexao) {

        conexao.setUsado(true);

        conexaoRepository.save(conexao);
    }

}