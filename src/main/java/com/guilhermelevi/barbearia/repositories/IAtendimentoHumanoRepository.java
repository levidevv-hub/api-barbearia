package com.guilhermelevi.barbearia.repositories;

import com.guilhermelevi.barbearia.domain.AtendimentoHumano;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface IAtendimentoHumanoRepository extends JpaRepository<AtendimentoHumano, Long> {
    record AvisoPendente(Long id, Long barbeiroId) {}
    @org.springframework.data.jpa.repository.Query("""
        select new com.guilhermelevi.barbearia.repositories.IAtendimentoHumanoRepository$AvisoPendente(a.id, a.barbeiro.id)
        from AtendimentoHumano a where a.estado = AGUARDANDO
        and a.avisoMensagemId is null and a.erroAviso is null order by a.solicitadoEm, a.id
        """)
    List<AvisoPendente> buscarAvisosPendentes(org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Query("""
        select a.id from AtendimentoHumano a
        where a.estado <> AUTOMATICO and (
            a.pausaAte <= :agora or
            (a.pausaAte is null and (a.solicitadoEm is null or a.solicitadoEm <= :limiteLegado))
        )
        order by a.solicitadoEm, a.id
        """)
    List<Long> buscarPausasExpiradas(@org.springframework.data.repository.query.Param("agora") java.time.LocalDateTime agora,
                                     @org.springframework.data.repository.query.Param("limiteLegado") java.time.LocalDateTime limiteLegado,
                                     org.springframework.data.domain.Pageable pageable);
    Optional<AtendimentoHumano> findByBarbeiroIdAndNumeroCliente(Long barbeiroId, String numero);
    Optional<AtendimentoHumano> findByIdAndBarbeiroId(Long id, Long barbeiroId);
    List<AtendimentoHumano> findTop100ByBarbeiroIdOrderBySolicitadoEmDesc(Long barbeiroId);
}
