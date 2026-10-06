package com.guilhermelevi.barbearia.admin;
import com.guilhermelevi.barbearia.domain.Barbeiro;
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

@WebMvcTest(AdminController.class) @Import(AdminSecurityConfig.class)
class AdminSecurityTest {
    @Autowired org.springframework.web.context.WebApplicationContext context;
    MockMvc mvc;
    @org.junit.jupiter.api.BeforeEach void setup() {
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    }
    @MockitoBean IBarbeiroRepository barbeiros;
    @MockitoBean ConexaoWhatsAppPendenteService conexoes;
    private static final String BODY="""
        {"nome":"Teste","numeroWhatsAppAdministrador":"5588999999999",
        "numeroWhatsAppNotificacao":"5588999999999","inicioExpediente":"08:00","fimExpediente":"18:00"}
        """;
    @Test void anonimoNaoAcessaDadosNemGeraLink() throws Exception {
        mvc.perform(get("/api/admin/barbeiros")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/meta/whatsapp/link/1").with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(barbeiros,conexoes);
    }
    @Test void csrfDisponivelAntesDoLogin() throws Exception {
        mvc.perform(get("/api/admin/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }
    @Test @WithMockUser(roles="ADMIN") void exigeCsrf() throws Exception {
        mvc.perform(post("/api/admin/barbeiros").contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(barbeiros);
    }
    @Test @WithMockUser(roles="USER") void outroPerfilNaoAcessa() throws Exception {
        mvc.perform(get("/api/admin/barbeiros")).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(roles="ADMIN") void cadastra() throws Exception {
        when(barbeiros.save(any())).thenAnswer(i->i.getArgument(0));
        mvc.perform(post("/api/admin/barbeiros").with(csrf()).contentType("application/json").content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nome").value("Teste"));
        verify(barbeiros).save(any());
    }
    @Test @WithMockUser(roles="ADMIN") void rejeitaIntervaloInvalido() throws Exception {
        mvc.perform(post("/api/admin/barbeiros").with(csrf()).contentType("application/json").content(BODY.replace("18:00","07:00")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(barbeiros);
    }
    @Test @WithMockUser(roles="ADMIN") void listaNaoExpoeCredenciais() throws Exception {
        Barbeiro b=new Barbeiro();b.setId(1L);b.setNome("Teste");b.setWhatsappAccessToken("segredo");b.setWhatsappPhoneNumberId("123");
        when(barbeiros.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(java.util.List.of(b));
        mvc.perform(get("/api/admin/barbeiros")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vinculoRegistrado").value(true))
                .andExpect(jsonPath("$[0].whatsappAccessToken").doesNotExist());
    }
    @Test @WithMockUser(roles="ADMIN") void geraLink() throws Exception {
        var token=java.util.UUID.randomUUID();when(conexoes.gerarToken(1L)).thenReturn(token);
        mvc.perform(post("/api/admin/barbeiros/1/link").with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.link").value("https://zaluratech.com.br/conectar-whatsapp?token="+token));
    }
    @Test void corsPermiteHostingerComCredenciais() throws Exception {
        mvc.perform(options("/api/admin/barbeiros").header("Origin","https://zaluratech.com.br")
                .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","content-type,x-csrf-token"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials","true"));
    }
    @Test void corsRejeitaOutroSite() throws Exception {
        mvc.perform(options("/api/admin/barbeiros").header("Origin","https://outro.example")
                .header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());
    }
}
