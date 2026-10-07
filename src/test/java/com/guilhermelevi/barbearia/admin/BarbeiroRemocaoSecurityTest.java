package com.guilhermelevi.barbearia.admin;
import org.junit.jupiter.api.BeforeEach;
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

@WebMvcTest(BarbeiroRemocaoController.class)
@Import(AdminSecurityConfig.class)
class BarbeiroRemocaoSecurityTest {
    @Autowired org.springframework.web.context.WebApplicationContext context;
    @MockitoBean BarbeiroRemocaoService remocao;
    MockMvc mvc;
    @BeforeEach void setup(){mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();}
    @Test void anonimoNaoRemove() throws Exception {
        mvc.perform(post("/api/admin/barbeiros/1/remover").with(csrf()).contentType("application/json").content("{\"nomeConfirmacao\":\"Teste\"}"))
                .andExpect(status().isUnauthorized());verifyNoInteractions(remocao);
    }
    @Test @WithMockUser(roles="ADMIN") void semCsrfNaoRemove() throws Exception {
        mvc.perform(post("/api/admin/barbeiros/1/remover").contentType("application/json").content("{\"nomeConfirmacao\":\"Teste\"}"))
                .andExpect(status().isForbidden());verifyNoInteractions(remocao);
    }
    @Test @WithMockUser(roles="USER") void usuarioComumNaoRemove() throws Exception {
        mvc.perform(post("/api/admin/barbeiros/1/remover").with(csrf()).contentType("application/json").content("{\"nomeConfirmacao\":\"Teste\"}"))
                .andExpect(status().isForbidden());verifyNoInteractions(remocao);
    }
    @Test @WithMockUser(roles="ADMIN") void adminRemoveComConfirmacao() throws Exception {
        mvc.perform(post("/api/admin/barbeiros/1/remover").with(csrf()).contentType("application/json").content("{\"nomeConfirmacao\":\"Teste\"}"))
                .andExpect(status().isNoContent());verify(remocao).remover(1L,"Teste");
    }
}
