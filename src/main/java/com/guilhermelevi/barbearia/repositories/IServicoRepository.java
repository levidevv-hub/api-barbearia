package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.Servico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IServicoRepository extends JpaRepository<Servico, Long> {

    List<Servico> findByBarbeiroId(Long barbeiroId);

    Optional<Servico> findByIdAndBarbeiroId(Long id, Long barbeiroId);

    List<Servico> findByBarbeiroIdAndAtivoTrue(Long barbeiroId);

    Optional<Servico> findByIdAndBarbeiroIdAndAtivoTrue(
            Long id,
            Long barbeiroId
    );

    boolean existsByIdAndBarbeiroIdAndAtivoTrue(
            Long id,
            Long barbeiroId
    );

}