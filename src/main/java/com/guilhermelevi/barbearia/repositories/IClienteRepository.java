package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface IClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByNumeroTelefone(String numero);
}
