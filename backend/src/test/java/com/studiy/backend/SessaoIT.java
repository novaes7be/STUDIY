package com.studiy.backend;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sessão em cookies HttpOnly, CSRF double-submit, refresh token rotativo e CORS. */
class SessaoIT extends IntegracaoBase {

    @Test
    void loginGravaCookiesHttpOnlySameSiteStrictESemTokenNoCorpo() throws Exception {
        Sessao s = novaSessao();
        MvcResult r = tentarLogin(s.email(), SENHA);
        List<String> setCookie = r.getResponse().getHeaders("Set-Cookie");

        assertThat(setCookie).anySatisfy(c -> assertThat(c).startsWith("studiy_at=")
                .contains("HttpOnly", "SameSite=Strict", "Path=/api"));
        assertThat(setCookie).anySatisfy(c -> assertThat(c).startsWith("studiy_rt=")
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/auth"));
        assertThat(r.getResponse().getContentAsString()).doesNotContain("token");
    }

    @Test
    void headerAuthorizationBearerNaoEhAceito() throws Exception {
        Sessao s = novaSessao();
        mvc.perform(get("/api/materias").header("Authorization", "Bearer " + s.acesso().getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void escritaSemCsrfOuComCsrfDivergenteEhRecusada() throws Exception {
        Sessao s = novaSessao();
        String corpo = corpo(Map.of("nome", "Química", "cor", "azul"));

        mvc.perform(post("/api/materias").cookie(s.acesso()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/materias").cookie(s.acesso(), new Cookie("XSRF-TOKEN", "a"))
                        .header("X-XSRF-TOKEN", "b").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mvc.perform(s.em(post("/api/materias")).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());
    }

    @Test
    void endpointCsrfEntregaCookieLegivelPeloFront() throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn();
        Cookie xsrf = r.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.isHttpOnly()).isFalse(); // o JS precisa ler para repetir no header
        assertThat(xsrf.getAttribute("SameSite")).isEqualTo("Strict");
    }

    @Test
    void refreshRotacionaEReusoDerrubaAFamilia() throws Exception {
        Sessao s = novaSessao();

        MvcResult r1 = mvc.perform(post("/api/auth/refresh").cookie(s.refresh()).with(comCsrf()))
                .andExpect(status().isOk()).andReturn();
        Cookie novoRefresh = r1.getResponse().getCookie("studiy_rt");
        Cookie novoAcesso = r1.getResponse().getCookie("studiy_at");
        assertThat(novoRefresh.getValue()).isNotEqualTo(s.refresh().getValue());
        mvc.perform(get("/api/materias").cookie(novoAcesso)).andExpect(status().isOk());

        // Reuso do refresh antigo (já trocado) = roubo provável: 401 e a família inteira cai
        mvc.perform(post("/api/auth/refresh").cookie(s.refresh()).with(comCsrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(novoRefresh).with(comCsrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidaAcessoERefresh() throws Exception {
        Sessao s = novaSessao();
        mvc.perform(s.em(post("/api/auth/logout"))).andExpect(status().isOk());
        mvc.perform(get("/api/materias").cookie(s.acesso())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(s.refresh()).with(comCsrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void trocaDeSenhaDerrubaOutrasSessoesEMantemEsta() throws Exception {
        Sessao s = novaSessao();
        Sessao outra = sessaoDe(s.email(), tentarLogin(s.email(), SENHA));

        MvcResult r = mvc.perform(s.em(put("/api/usuarios/me/senha")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("senhaAtual", SENHA, "novaSenha", "outra-senha-forte-456"))))
                .andExpect(status().isOk()).andReturn();

        mvc.perform(get("/api/materias").cookie(outra.acesso())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(outra.refresh()).with(comCsrf())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/materias").cookie(r.getResponse().getCookie("studiy_at"))).andExpect(status().isOk());
    }

    @Test
    void corsSoLiberaOrigemExataComCredenciais() throws Exception {
        mvc.perform(options("/api/materias").header("Origin", "http://localhost:5500")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5500"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/materias").header("Origin", "http://localhost:6666")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
