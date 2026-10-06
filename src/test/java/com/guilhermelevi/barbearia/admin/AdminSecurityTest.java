package com.guilhermelevi.barbearia.admin;

import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import com.guilhermelevi.barbearia.service.ConexaoWhatsAppPendenteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest(AdminController.class)
@Import(AdminSecurityConfig.class)
class AdminSecurityTest {
    @Autowired org.springframework.web.context.WebApplicationContext context;
    MockMvc mvc;
    @org.junit.jupiter.api.BeforeEach
    void configurarSegurancaMockMvc() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }
    @MockitoBean IBarbeiroRepository barbeiros;
    @MockitoBean ConexaoWhatsAppPendenteService conexoes;

    @Test void anonimoNaoAcessaPainelNemGeraLink() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/meta/whatsapp/link/1").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/barbeiros/1/link").with(csrf())).andExpect(status().is3xxRedirection());
        verifyNoInteractions(conexoes, barbeiros);
    }
    @Test void loginRenderizaComCsrf() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
    }
    @Test @WithMockUser(roles="ADMIN") void exigeCsrfParaCadastrar() throws Exception {
        mvc.perform(post("/admin/barbeiros")).andExpect(status().isForbidden());
        verifyNoInteractions(barbeiros);
    }
    @Test @WithMockUser(roles="USER") void outroPerfilNaoAcessa() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(roles="ADMIN") void cadastroValidoSalvaERedireciona() throws Exception {
        mvc.perform(post("/admin/barbeiros").with(csrf())
                .param("nome","Barbearia Teste")
                .param("numeroWhatsAppAdministrador","5588999999999")
                .param("numeroWhatsAppNotificacao","5588999999999")
                .param("inicioExpediente","08:00").param("fimExpediente","18:00"))
                .andExpect(redirectedUrl("/admin"));
        verify(barbeiros).save(argThat(b -> b.getNome().equals("Barbearia Teste") && b.getWhatsappAccessToken() == null));
    }
    @Test @WithMockUser(roles="ADMIN") void rejeitaIntervaloInvalido() throws Exception {
        mvc.perform(post("/admin/barbeiros").with(csrf())
                .param("nome","Teste").param("numeroWhatsAppAdministrador","5588999999999")
                .param("numeroWhatsAppNotificacao","5588999999999")
                .param("inicioExpediente","18:00").param("fimExpediente","08:00"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form","fimExpediente"));
        verifyNoInteractions(barbeiros);
    }
    @Test @WithMockUser(roles="ADMIN") void geraLinkComToken() throws Exception {
        var token = java.util.UUID.randomUUID();
        when(conexoes.gerarToken(1L)).thenReturn(token);
        mvc.perform(post("/admin/barbeiros/1/link").with(csrf()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("link", "https://zaluratech.com.br/conectar-whatsapp?token=" + token));
    }
}
