package com.guilhermelevi.barbearia.admin;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.repositories.*;
import com.guilhermelevi.barbearia.service.BloqueioDataService;
import com.guilhermelevi.barbearia.domain.exception.OperacaoAdministrativaException;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

/** Extensão do painel. Reutiliza autenticação ADMIN, CSRF e CORS existentes. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/barbeiros/{id}")
public class BarbeiroConfiguracaoController {
    private final IBarbeiroRepository barbeiros;
    private final IExpedienteSemanalRepository expedientes;
    private final IPeriodoExpedienteRepository periodos;
    private final IBloqueioDataRepository bloqueios;
    private final BloqueioDataService bloqueioService;
    private final EntityManager em;

    public record Periodo(@NotNull LocalTime inicio, @NotNull LocalTime fim) {}
    public record Dia(@NotNull DayOfWeek diaSemana, boolean aberto,
                      @NotNull @Size(max=12) List<@NotNull @Valid Periodo> periodos) {}
    public record Configuracao(@NotNull @Size(min=7,max=7) List<@NotNull @Valid Dia> semana,
                               @Size(max=255) String endereco,
                               Double latitude, Double longitude) {}
    public record Bloqueio(@NotNull @FutureOrPresent LocalDate data,
                           @NotBlank @Size(max=255) String motivo) {}
    public record Liberacao(@NotNull @FutureOrPresent LocalDate data) {}
    public record BloqueioResumo(LocalDate data, String motivo) {}

    private Barbeiro buscar(Long id, boolean travar) {
        return (travar ? barbeiros.buscarParaAgendar(id) : barbeiros.findById(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Barbeiro não encontrado."));
    }

    @GetMapping("/configuracao")
    @Transactional(readOnly=true)
    public Configuracao consultar(@PathVariable Long id) {
        Barbeiro b = buscar(id, false);
        List<Dia> semana = new ArrayList<>();
        for (DayOfWeek dia : DayOfWeek.values()) {
            ExpedienteSemanal e = expedientes.findByBarbeiroIdAndDiaSemana(id, dia).orElse(null);
            List<Periodo> turnos = e == null ? List.of() : periodos
                    .findByExpedienteSemanalIdOrderByInicioAsc(e.getId()).stream()
                    .map(p -> new Periodo(p.getInicio(), p.getFim())).toList();
            semana.add(new Dia(dia, e != null && e.isAberto(), turnos));
        }
        return new Configuracao(semana, b.getEndereco(), b.getLatitude(), b.getLongitude());
    }

    static void validar(Configuracao c) {
        if (c.semana() == null || c.semana().size() != 7)
            throw new IllegalArgumentException("Informe os sete dias da semana.");
        Set<DayOfWeek> dias = new HashSet<>();
        for (Dia d : c.semana()) {
            if (d == null || d.diaSemana() == null || !dias.add(d.diaSemana()))
                throw new IllegalArgumentException("Informe cada dia da semana uma única vez.");
            if (d.periodos() == null || d.periodos().size() > 12)
                throw new IllegalArgumentException("Informe até 12 períodos por dia.");
            if (!d.aberto() && !d.periodos().isEmpty())
                throw new IllegalArgumentException("Dias fechados não devem ter períodos.");
            if (d.aberto() && d.periodos().isEmpty())
                throw new IllegalArgumentException("Dias abertos precisam de pelo menos um período.");
            for (Periodo p : d.periodos()) {
                if (p == null || p.inicio() == null || p.fim() == null || !p.fim().isAfter(p.inicio()))
                    throw new IllegalArgumentException("O fim de cada período deve ser depois do início, no mesmo dia.");
                if (p.inicio().getSecond() != 0 || p.fim().getSecond() != 0 || p.inicio().getNano() != 0 || p.fim().getNano() != 0)
                    throw new IllegalArgumentException("Use horários com precisão de minutos.");
            }
            LocalTime fim = null;
            for (Periodo p : d.periodos().stream().sorted(Comparator.comparing(Periodo::inicio)).toList()) {
                if (fim != null && p.inicio().isBefore(fim))
                    throw new IllegalArgumentException("Os períodos do mesmo dia não podem se sobrepor.");
                fim = p.fim();
            }
        }
        if ((c.latitude() == null) != (c.longitude() == null))
            throw new IllegalArgumentException("Informe latitude e longitude juntas.");
        if (c.latitude() != null && (!Double.isFinite(c.latitude()) || !Double.isFinite(c.longitude())
                || Math.abs(c.latitude()) > 90 || Math.abs(c.longitude()) > 180))
            throw new IllegalArgumentException("Coordenadas inválidas.");
    }

    @PostMapping("/configuracao")
    @Transactional
    public Configuracao salvar(@PathVariable Long id, @Valid @RequestBody Configuracao c) {
        validar(c);
        // Mesma trava usada pelo agendamento: evita atualização parcial concorrente.
        Barbeiro b = buscar(id, true);
        b.setEndereco(c.endereco() == null ? null : c.endereco().strip());
        b.setLatitude(c.latitude());
        b.setLongitude(c.longitude());
        for (Dia d : c.semana()) {
            ExpedienteSemanal e = expedientes.findByBarbeiroIdAndDiaSemana(id, d.diaSemana())
                    .orElseGet(ExpedienteSemanal::new);
            List<Periodo> novos = d.periodos().stream().sorted(Comparator.comparing(Periodo::inicio)).toList();
            e.setBarbeiro(b);
            e.setDiaSemana(d.diaSemana());
            e.setAberto(d.aberto());
            e.setInicio(novos.isEmpty() ? null : novos.get(0).inicio());
            e.setFim(novos.isEmpty() ? null : novos.get(novos.size()-1).fim());
            expedientes.saveAndFlush(e);
            periodos.deleteAll(periodos.findByExpedienteSemanalIdOrderByInicioAsc(e.getId()));
            periodos.flush(); // Remove chaves únicas antes de recriar períodos.
            for (Periodo p : novos) {
                PeriodoExpediente turno = new PeriodoExpediente();
                turno.setExpedienteSemanal(e);
                turno.setInicio(p.inicio());
                turno.setFim(p.fim());
                periodos.save(turno);
            }
        }
        // Campos antigos são apenas resumo; o bot usa a semana e seus períodos.
        var todos = c.semana().stream().flatMap(d -> d.periodos().stream()).toList();
        if (!todos.isEmpty()) {
            b.setInicioExpediente(todos.stream().map(Periodo::inicio).min(LocalTime::compareTo).orElseThrow());
            b.setFimExpediente(todos.stream().map(Periodo::fim).max(LocalTime::compareTo).orElseThrow());
        }
        em.flush();
        return consultar(id);
    }

    @GetMapping("/bloqueios")
    @Transactional(readOnly=true)
    public List<BloqueioResumo> listarBloqueios(@PathVariable Long id) {
        buscar(id, false);
        return em.createQuery("select b from BloqueioData b where b.barbeiro.id = :id and b.data >= :hoje order by b.data", BloqueioData.class)
                .setParameter("id", id).setParameter("hoje", LocalDate.now()).getResultList().stream()
                .map(b -> new BloqueioResumo(b.getData(), b.getMotivo())).toList();
    }

    @PostMapping("/bloqueios")
    @Transactional
    public Map<String,String> bloquear(@PathVariable Long id, @Valid @RequestBody Bloqueio form) {
        Barbeiro b = buscar(id, true);
        if (bloqueios.existsByBarbeiroIdAndData(id, form.data()))
            throw new IllegalArgumentException("Essa data já está bloqueada.");
        // Nunca cancela reservas nem dispara mensagens a partir desta tela.
        if (!bloqueioService.consultarAfetados(id, form.data()).isEmpty())
            throw new IllegalArgumentException("Há agendamentos nesta data. Gerencie os cancelamentos antes de bloquear o dia.");
        BloqueioData bloqueio = new BloqueioData();
        bloqueio.setBarbeiro(b);
        bloqueio.setData(form.data());
        bloqueio.setMotivo(form.motivo().strip());
        bloqueios.save(bloqueio);
        return Map.of("mensagem", "Dia bloqueado.");
    }

    @PostMapping("/bloqueios/liberar")
    @Transactional
    public Map<String,String> liberar(@PathVariable Long id, @Valid @RequestBody Liberacao form) {
        buscar(id, true);
        bloqueios.deleteByBarbeiroIdAndData(id, form.data());
        return Map.of("mensagem", "Dia liberado. O expediente semanal continua valendo.");
    }

    @ExceptionHandler({IllegalArgumentException.class, OperacaoAdministrativaException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> invalido(RuntimeException e) { return Map.of("erro", e.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> validacao(MethodArgumentNotValidException e) {
        return Map.of("erro", e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(java.util.stream.Collectors.joining("; ")));
    }
}
