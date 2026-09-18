package com.guilhermelevi.barbearia.service;

import com.guilhermelevi.barbearia.domain.Cliente;
import com.guilhermelevi.barbearia.repositories.IClienteRepository;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ClienteService {

    private final IClienteRepository repository;

    public Cliente buscarOuCriar(String nome, String telefone) {

        return repository.findByNumeroTelefone(telefone)
                .orElseGet(() ->
                        repository.save(
                                new Cliente(nome, telefone)
                        )
                );
    }
}