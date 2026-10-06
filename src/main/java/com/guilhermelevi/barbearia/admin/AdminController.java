package com.guilhermelevi.barbearia.admin;

import com.guilhermelevi.barbearia.domain.Barbeiro;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import com.guilhermelevi.barbearia.service.ConexaoWhatsAppPendenteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor
public class AdminController {
    private final IBarbeiroRepository barbeiros;
    private final ConexaoWhatsAppPendenteService conexoes;

    @GetMapping("/login")
    public String login() { return "admin/login"; }

    @GetMapping("/admin")
    public String painel(Model model) {
        model.addAttribute("barbeiros", barbeiros.findAll(Sort.by(Sort.Direction.DESC, "id")));
        return "admin/index";
    }

    @GetMapping("/admin/barbeiros/novo")
    public String novo(Model model) {
        model.addAttribute("form", new BarbeiroForm());
        return "admin/form";
    }

    @PostMapping("/admin/barbeiros")
    public String cadastrar(@Valid @ModelAttribute("form") BarbeiroForm form,
                            BindingResult result, RedirectAttributes flash) {
        if (form.getInicioExpediente() != null && form.getFimExpediente() != null
                && !form.getFimExpediente().isAfter(form.getInicioExpediente())) {
            result.rejectValue("fimExpediente", "intervalo", "O fim deve ser depois do inicio.");
        }
        if (result.hasErrors()) return "admin/form";
        Barbeiro barbeiro = new Barbeiro();
        barbeiro.setNome(form.getNome().strip());
        barbeiro.setNumeroWhatsAppAdministrador(form.getNumeroWhatsAppAdministrador());
        barbeiro.setNumeroWhatsAppNotificacao(form.getNumeroWhatsAppNotificacao());
        barbeiro.setInicioExpediente(form.getInicioExpediente());
        barbeiro.setFimExpediente(form.getFimExpediente());
        barbeiro.setEndereco(form.getEndereco() == null ? null : form.getEndereco().strip());
        barbeiros.save(barbeiro);
        flash.addFlashAttribute("sucesso", "Barbeiro cadastrado. Agora gere o link para conectar o WhatsApp.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/barbeiros/{id}/link")
    public String gerarLink(@PathVariable Long id, RedirectAttributes flash) {
        try {
            flash.addFlashAttribute("link", "https://zaluratech.com.br/conectar-whatsapp?token=" + conexoes.gerarToken(id));
            flash.addFlashAttribute("linkBarbeiroId", id);
        } catch (IllegalArgumentException erro) {
            flash.addFlashAttribute("erro", erro.getMessage());
        }
        return "redirect:/admin";
    }
}
