package com.studiy.backend;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A cadeia de segurança da documentação (só no perfil dev) não pode ficar sem CSRF. */
class SwaggerCsrfIT extends IntegracaoBase {

    @Test
    void documentacaoContinuaAbrindoSemLogin() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Studiy API")));
    }

    @Test
    void escritaNosCaminhosDaDocumentacaoSemTokenCsrfEBarrada() throws Exception {
        mvc.perform(post("/v3/api-docs")).andExpect(status().isForbidden());
        mvc.perform(post("/swagger-ui/index.html")).andExpect(status().isForbidden());
    }
}
