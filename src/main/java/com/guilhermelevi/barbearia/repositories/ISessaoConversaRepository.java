package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.SessaoConversa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ISessaoConversaRepository extends JpaRepository<SessaoConversa, Long> {

    Optional<SessaoConversa> findByNumeroClienteAndBarbeiroId(
            String numeroCliente,
            Long barbeiroId
    );

}
