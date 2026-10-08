package com.guilhermelevi.barbearia.admin;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.domain.PreviaBloqueio;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BarbeiroRemocaoService {
    private final IBarbeiroRepository barbeiros;
    private final EntityManager em;

    @Transactional
    public void remover(Long id, String nomeConfirmacao) {
        Barbeiro b = barbeiros.buscarParaAgendar(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profissional não encontrado."));
        if (nomeConfirmacao == null || !nomeConfirmacao.equals(b.getNome()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Digite o nome exato do profissional para confirmar.");

        // Histórico de qualquer status é preservado, inclusive cancelamentos.
        long reservas = em.createQuery("select count(a) from Agendamento a where a.barbeiro.id = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (reservas > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este profissional possui histórico de agendamentos e não pode ser excluído. Para interromper novos agendamentos, feche todos os dias em Configurar.");

        // Remoção por entidade para limpar também a coleção de IDs da prévia.
        for (PreviaBloqueio previa : em.createQuery("select p from PreviaBloqueio p where p.barbeiro.id = :id", PreviaBloqueio.class)
                .setParameter("id", id).getResultList()) em.remove(previa);
        em.flush();
        for (var sessao : em.createQuery("select s from SessaoConversa s where s.barbeiro.id = :id",
                com.guilhermelevi.barbearia.domain.SessaoConversa.class).setParameter("id", id).getResultList()) {
            em.remove(sessao);
        }
        em.flush();
        excluir("delete from ConexaoWhatsAppPendente c where c.barbeiro.id = :id", id);
        excluir("delete from BloqueioData b where b.barbeiro.id = :id", id);
        excluir("delete from PeriodoExpediente p where p.expedienteSemanal.id in (select e.id from ExpedienteSemanal e where e.barbeiro.id = :id)", id);
        excluir("delete from ExpedienteSemanal e where e.barbeiro.id = :id", id);
        excluir("delete from Servico s where s.barbeiro.id = :id", id);
        // Bulk deletes não removem as entidades do contexto de persistência.
        // Desanexa as referências já excluídas antes de remover o pai; a trava
        // de banco permanece retida até o fim da transação.
        em.flush();
        em.clear();
        barbeiros.deleteById(id);
        em.flush();
    }

    private void excluir(String jpql, Long id) { em.createQuery(jpql).setParameter("id", id).executeUpdate(); }
}
