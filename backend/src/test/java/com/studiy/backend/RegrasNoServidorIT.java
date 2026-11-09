package com.studiy.backend;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tudo que o front exibe e que depende de regra vem pronto da API. */
class RegrasNoServidorIT extends IntegracaoBase {

    @Test
    void perfilTrazPlanoUsoEGamificacaoCalculados() throws Exception {
        Sessao s = novaSessao(); // o login só mantém o streak: conta nova começa com 0 XP
        JsonNode p = ler(mvc.perform(get("/api/usuarios/me").cookie(s.acesso())).andReturn());

        assertThat(p.path("planoRotulo").asText()).isEqualTo("Plano Grátis");
        assertThat(p.path("acaoPlano").asText()).isEqualTo("Assinar Pro");
        assertThat(p.path("usoPercentual").asInt()).isZero();
        assertThat(p.path("regraUso").asText()).isNotBlank();
        JsonNode g = p.path("gamificacao");
        assertThat(g.path("xp").asInt()).isZero();
        assertThat(g.path("nivel").asInt()).isEqualTo(1);
        assertThat(g.path("streakAtual").asInt()).isEqualTo(1);
        assertThat(g.path("xpNoNivel").asInt()).isZero();
        assertThat(g.path("xpParaProximoNivel").asInt()).isEqualTo(100);
        assertThat(g.path("percentualNivel").asInt()).isZero();
    }

    @Test
    void onboardingAparecePraContaNovaAteSerConcluido() throws Exception {
        Sessao s = novaSessao();
        assertThat(ler(mvc.perform(get("/api/usuarios/me").cookie(s.acesso())).andReturn())
                .path("onboardingPendente").asBoolean()).isTrue();
        mvc.perform(s.em(post("/api/usuarios/me/onboarding"))).andExpect(status().isOk());
        assertThat(ler(mvc.perform(get("/api/usuarios/me").cookie(s.acesso())).andReturn())
                .path("onboardingPendente").asBoolean()).isFalse();
    }

    @Test
    void revisaoTrazOsBotoesEAceitaSoOsValoresDoServidor() throws Exception {
        Sessao s = novaSessao();
        long m = ler(mvc.perform(s.em(post("/api/materias")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", "Rev", "cor", "azul")))).andReturn()).path("id").asLong();

        JsonNode rev = ler(mvc.perform(get("/api/materias/" + m + "/flashcards/revisao").cookie(s.acesso())).andReturn());
        assertThat(rev.path("cartoes").isArray()).isTrue();
        assertThat(rev.path("opcoes")).extracting(o -> o.path("valor").asText())
                .containsExactly("ERREI", "DIFICIL", "BOM", "FACIL");
    }

    @Test
    void corDaMateriaAceitaSoAsChavesDaPaleta() throws Exception {
        Sessao s = novaSessao();
        JsonNode m = ler(mvc.perform(s.em(post("/api/materias")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", "Botânica", "cor", "verde")))).andExpect(status().isOk()).andReturn());
        assertThat(m.path("cor").asText()).isEqualTo("verde");
        JsonNode padrao = ler(mvc.perform(s.em(post("/api/materias")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", "Sem cor")))).andExpect(status().isOk()).andReturn());
        assertThat(padrao.path("cor").asText()).isEqualTo("verde");
        mvc.perform(s.em(post("/api/materias")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", "X", "cor", "#ff0000")))).andExpect(status().isBadRequest());
    }

    @Test
    void cadastroSemAceiteDosTermosEhRecusadoNoServidor() throws Exception {
        mvc.perform(post("/api/auth/registrar").with(comCsrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("nome", "X", "email", emailNovo(), "senha", SENHA, "aceitoTermos", false))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void configDePagamentoTrazPrecoFormatado() throws Exception {
        Sessao s = novaSessao();
        JsonNode c = ler(mvc.perform(get("/api/pagamentos/config").cookie(s.acesso())).andReturn());
        assertThat(c.path("precoFormatado").asText()).isEqualTo("R$ 20,00");
    }
}
