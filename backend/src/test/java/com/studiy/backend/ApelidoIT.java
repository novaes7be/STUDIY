package com.studiy.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;


import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Apelido do ranking: formato, unicidade sem diferenciar maiúsculas, reservados e opt-out. */
class ApelidoIT extends IntegracaoBase {

    private org.springframework.test.web.servlet.ResultActions definir(Sessao s, String apelido) throws Exception {
        return mvc.perform(s.em(put("/api/usuarios/me/apelido")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(java.util.Collections.singletonMap("apelido", apelido))));
    }

    @Test
    void comecaForaDoRankingEDefineApelido() throws Exception {
        Sessao s = novaSessao();
        assertThat(ler(mvc.perform(get("/api/usuarios/me").cookie(s.acesso())).andReturn()).get("apelido").isNull()).isTrue();

        String apelido = "Estudante_" + System.nanoTime() % 100000;
        definir(s, apelido).andExpect(status().isOk());
        assertThat(ler(mvc.perform(get("/api/usuarios/me").cookie(s.acesso())).andReturn()).get("apelido").asText())
                .isEqualTo(apelido);
    }

    @Test
    void rejeitaFormatoInvalidoEReservado() throws Exception {
        Sessao s = novaSessao();
        definir(s, "ab").andExpect(status().isBadRequest());                 // curto demais
        definir(s, "nome com espaço").andExpect(status().isBadRequest());    // espaço
        definir(s, "<script>").andExpect(status().isBadRequest());           // caracteres fora da lista
        definir(s, "x".repeat(21)).andExpect(status().isBadRequest());       // longo demais
        definir(s, "Suporte").andExpect(status().isBadRequest());            // imita a equipe
    }

    @Test
    void apelidoEhUnicoSemDiferenciarMaiusculas() throws Exception {
        Sessao a = novaSessao(), b = novaSessao();
        String apelido = "Unico" + System.nanoTime() % 100000;
        definir(a, apelido).andExpect(status().isOk());
        definir(b, apelido.toUpperCase()).andExpect(status().isConflict());
        definir(a, apelido.toLowerCase()).andExpect(status().isOk());       // o próprio dono pode mudar a caixa
    }

    @Test
    void vazioRemoveDoRanking() throws Exception {
        Sessao s = novaSessao();
        definir(s, "Sai" + System.nanoTime() % 100000).andExpect(status().isOk());
        definir(s, "").andExpect(status().isOk());
        assertThat(ler(mvc.perform(get("/api/usuarios/me").cookie(s.acesso())).andReturn()).get("apelido").isNull()).isTrue();
    }
}
