package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface IBarbeiroRepository extends JpaRepository<Barbeiro, Long> {

    Optional<Barbeiro> findByWhatsappPhoneNumberId(String numero);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Barbeiro b where b.id = :id")
    Optional<Barbeiro> buscarParaAgendar(@Param("id") Long id);
}