package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.MensagemAtendimento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface IMensagemAtendimentoRepository extends JpaRepository<MensagemAtendimento, Long> {
    List<MensagemAtendimento> findTop100ByAtendimentoIdOrderByIdDesc(Long atendimentoId);
}
