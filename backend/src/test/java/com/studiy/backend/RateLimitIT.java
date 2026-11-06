package com.studiy.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * O rate limit roda na cadeia do Spring Security DEPOIS do JwtAuthFilter: o limite de IA é por
 * usuário de verdade (dois usuários no mesmo IP têm limites separados) e cada requisição consome
 * um único token (o filtro não está registrado duas vezes).
 */
@TestPropertySource(properties = "studiy.ratelimit.ia-por-minuto=2")
class RateLimitIT extends IntegracaoBase {

    private int gerar(Sessao s, long materia) throws Exception {
        return mvc.perform(s.em(post("/api/materias/" + materia + "/gerar")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("pdfId", 999999, "tipo", "RESUMO")))).andReturn().getResponse().getStatus();
    }

    private long materia(Sessao s) throws Exception {
        return ler(mvc.perform(s.em(post("/api/materias")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", "Rate", "cor", "azul")))).andReturn()).path("id").asLong();
    }

    @Test
    void limiteDeIaEhPorUsuarioEContaUmaVezPorRequisicao() throws Exception {
        Sessao ana = novaSessao();
        Sessao beto = novaSessao(); // mesmo IP (127.0.0.1) que a Ana
        long mAna = materia(ana);
        long mBeto = materia(beto);

        assertThat(gerar(ana, mAna)).isEqualTo(404); // passou do limite; PDF não existe
        assertThat(gerar(ana, mAna)).isEqualTo(404); // 2ª ainda passa: o filtro consumiu 1 token por chamada
        assertThat(gerar(ana, mAna)).isEqualTo(429);

        assertThat(gerar(beto, mBeto)).isEqualTo(404); // outro usuário, mesmo IP: bucket próprio
    }
}
