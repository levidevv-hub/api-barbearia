package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.ConexaoWhatsAppPendente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IConexaoWhatsAppPendenteRepository
        extends JpaRepository<ConexaoWhatsAppPendente, Long> {

    Optional<ConexaoWhatsAppPendente> findByToken(UUID token);
}