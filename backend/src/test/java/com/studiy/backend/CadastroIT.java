package com.studiy.backend;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cadastro sem enumeração de e-mail e verificação obrigatória para IA/pagamento. */
class CadastroIT extends IntegracaoBase {

    private MvcResult registrar(String email) throws Exception {
        return mvc.perform(post("/api/auth/registrar").with(comCsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", "Teste", "email", email, "senha", SENHA, "aceitoTermos", true)))).andReturn();
    }

    @Test
    void respostaEhIdenticaParaEmailNovoEJaCadastrado() throws Exception {
        String email = emailNovo();
        MvcResult novo = registrar(email);
        MvcResult repetido = registrar(email);

        assertThat(novo.getResponse().getStatus()).isEqualTo(202);
        assertThat(repetido.getResponse().getStatus()).isEqualTo(202);
        assertThat(repetido.getResponse().getContentAsString()).isEqualTo(novo.getResponse().getContentAsString());
        assertThat(novo.getResponse().getContentAsString()).doesNotContain("token");

        verify(emailService).enviarVerificacao(eq(email), org.mockito.ArgumentMatchers.anyString());
        verify(emailService).enviarAvisoContaExistente(email);
    }

    @Test
    void emailComMaiusculasTambemEhReconhecidoComoExistente() throws Exception {
        String email = emailNovo();
        registrar(email);
        registrar(email.toUpperCase());
        verify(emailService).enviarAvisoContaExistente(email);
    }

    @Test
    void contaNaoVerificadaEntraMasNaoUsaIaNemPagamento() throws Exception {
        String email = emailNovo();
        registrar(email);
        Sessao s = sessaoDe(email, tentarLogin(email, SENHA));
        assertThat(s.acesso()).isNotNull();

        long materia = ler(mvc.perform(s.em(post("/api/materias")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", "Física", "cor", "azul")))).andReturn()).path("id").asLong();

        mvc.perform(s.em(post("/api/materias/" + materia + "/gerar")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("pdfId", 999999, "tipo", "RESUMO"))))
                .andExpect(status().isForbidden());
        mvc.perform(s.em(post("/api/pagamentos/checkout")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("metodo", "PIX"))))
                .andExpect(status().isForbidden());

        // Confirma o e-mail com o token que teria ido por e-mail
        ArgumentCaptor<String> tk = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarVerificacao(eq(email), tk.capture());
        mvc.perform(post("/api/auth/verificar-email").with(comCsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("token", tk.getValue())))).andExpect(status().isOk());

        // Agora passa da verificação (o PDF não existe → 404, não mais 403)
        mvc.perform(s.em(post("/api/materias/" + materia + "/gerar")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("pdfId", 999999, "tipo", "RESUMO"))))
                .andExpect(status().isNotFound());
        mvc.perform(s.em(post("/api/pagamentos/checkout")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("metodo", "PIX"))))
                .andExpect(status().isOk());

        // Token de verificação é de uso único
        mvc.perform(post("/api/auth/verificar-email").with(comCsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("token", tk.getValue())))).andExpect(status().isBadRequest());
    }

    @Test
    void reenviarVerificacaoNaoRevelaSeOEmailExiste() throws Exception {
        String inexistente = emailNovo();
        String r1 = mvc.perform(post("/api/auth/reenviar-verificacao").with(comCsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("email", inexistente)))).andReturn().getResponse().getContentAsString();
        String existente = emailNovo();
        registrar(existente);
        String r2 = mvc.perform(post("/api/auth/reenviar-verificacao").with(comCsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("email", existente)))).andReturn().getResponse().getContentAsString();
        assertThat(r1).isEqualTo(r2);
        verify(emailService, never()).enviarVerificacao(eq(inexistente), org.mockito.ArgumentMatchers.anyString());
    }
}
