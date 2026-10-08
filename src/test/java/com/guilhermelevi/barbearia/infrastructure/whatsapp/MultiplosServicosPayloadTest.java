package com.guilhermelevi.barbearia.infrastructure.whatsapp;

import com.guilhermelevi.barbearia.domain.*;
import com.guilhermelevi.barbearia.repositories.IBarbeiroRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.mock.http.client.MockClientHttpRequest;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MultiplosServicosPayloadTest {
    @Test void resumoEConfirmacaoCabemNoWhatsAppComOitoNomesMaximos() {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        var repo=mock(IBarbeiroRepository.class);
        when(repo.findByWhatsappPhoneNumberId("phone")).thenReturn(Optional.of(Barbeiro.builder().whatsappAccessToken("teste").build()));
        var client=new WhatsAppClient(builder,repo);ReflectionTestUtils.setField(client,"apiVersion","v26.0");
        var sessao=SessaoConversa.builder().dataSelecionada(LocalDate.now().plusDays(1)).horarioSelecionado(LocalTime.NOON)
                .confirmacaoId(UUID.randomUUID()).precoServicoNaConfirmacao(new BigDecimal("79999999999.92")).duracaoServicoNaConfirmacao(1432).build();
        for(long i=1;i<=8;i++) sessao.getItensSelecionados().add(new ItemServico(i,"S".repeat(99)+i,new BigDecimal("9999999999.99"),179));
        for(int i=0;i<2;i++) server.expect(requestTo("https://graph.facebook.com/v26.0/phone/messages"))
                .andExpect(request->{
                    var body=new JsonMapper().readTree(((MockClientHttpRequest)request).getBodyAsString());
                    var interactive=body.path("interactive");var text=interactive.path("body").path("text").asText();
                    assertTrue(text.length()<=1024,"Corpo excede 1024: "+text.length());
                    assertTrue(text.contains("S".repeat(99)+8));
                    var buttons=interactive.path("action").path("buttons");assertTrue(buttons.size()<=3);
                    for(var button:buttons) assertTrue(button.path("reply").path("title").asText().length()<=20);
                }).andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.teste\"}]}",MediaType.APPLICATION_JSON));
        client.enviarResumoServicos("phone","numero",sessao);client.enviarConfirmacao("phone","numero",sessao);server.verify();
    }
}
