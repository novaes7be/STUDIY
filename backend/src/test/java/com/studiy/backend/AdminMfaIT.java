package com.studiy.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.studiy.backend.model.Papel;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.security.Totp;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/admin/** exige papel ADMIN + MFA ativo + sessão verificada por TOTP. */
class AdminMfaIT extends IntegracaoBase {

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Sessao promoverAdmin() throws Exception {
        Sessao s = novaSessao();
        Usuario u = usuarios.findByEmail(s.email()).orElseThrow();
        u.setPapel(Papel.ADMIN);
        usuarios.save(u);
        return s;
    }

    private MvcResult codigo(Sessao s, String rota, String codigo) throws Exception {
        return mvc.perform(s.em(post("/api/auth/mfa/" + rota)).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("codigo", codigo)))).andReturn();
    }

    private static String codigoAtual(String segredo, int deslocamento) {
        return Totp.codigo(segredo, Totp.passoAtual(Instant.now()) + deslocamento);
    }

    @Test
    void usuarioComumNuncaAcessaAdminNemConfiguraMfa() throws Exception {
        Sessao s = novaSessao();
        mvc.perform(get("/api/admin/usuarios").cookie(s.acesso())).andExpect(status().isForbidden());
        mvc.perform(s.em(post("/api/auth/mfa/configurar"))).andExpect(status().isForbidden());
    }

    @Test
    void adminSoComSenhaNaoAcessaPainel() throws Exception {
        Sessao s = promoverAdmin();
        mvc.perform(get("/api/admin/usuarios").cookie(s.acesso())).andExpect(status().isForbidden());
    }

    @Test
    void fluxoCompletoConfigurarAtivarEVerificar() throws Exception {
        Sessao s = promoverAdmin();
        JsonNode cfg = ler(mvc.perform(s.em(post("/api/auth/mfa/configurar"))).andExpect(status().isOk()).andReturn());
        String segredo = cfg.path("segredo").asText();
        assertThat(cfg.path("uri").asText()).startsWith("otpauth://totp/Studiy:");
        // o segredo fica cifrado no banco (leitura crua da coluna, sem o conversor JPA)
        String cru = jdbc.queryForObject("select mfa_segredo from usuarios where email = ?", String.class, s.email());
        assertThat(cru).startsWith("enc:v1:").doesNotContain(segredo);

        assertThat(codigo(s, "ativar", "000000").getResponse().getStatus()).isEqualTo(401);
        MvcResult ativado = codigo(s, "ativar", codigoAtual(segredo, -1));
        assertThat(ativado.getResponse().getStatus()).isEqualTo(200);
        Cookie acessoComMfa = ativado.getResponse().getCookie("studiy_at");

        mvc.perform(get("/api/admin/usuarios").cookie(acessoComMfa)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/pedidos").cookie(acessoComMfa)).andExpect(status().isOk());
        JsonNode uso = ler(mvc.perform(get("/api/admin/uso-ia").cookie(acessoComMfa)).andExpect(status().isOk()).andReturn());
        assertThat(uso.path("custoTotal").asText()).startsWith("US$ ");

        // A sessão antiga (só senha) continua sem acesso; o MFA renova junto com o refresh
        mvc.perform(get("/api/admin/usuarios").cookie(s.acesso())).andExpect(status().isForbidden());
        MvcResult renovado = mvc.perform(post("/api/auth/refresh").cookie(ativado.getResponse().getCookie("studiy_rt"))
                .with(comCsrf())).andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/admin/usuarios").cookie(renovado.getResponse().getCookie("studiy_at"))).andExpect(status().isOk());

        // Novo login: precisa do TOTP de novo, e o código já usado não vale
        Sessao nova = sessaoDe(s.email(), tentarLogin(s.email(), SENHA));
        mvc.perform(get("/api/admin/usuarios").cookie(nova.acesso())).andExpect(status().isForbidden());
        assertThat(codigo(nova, "verificar", codigoAtual(segredo, -1)).getResponse().getStatus()).isEqualTo(401);
        MvcResult verificado = codigo(nova, "verificar", codigoAtual(segredo, 0));
        assertThat(verificado.getResponse().getStatus()).isEqualTo(200);
        mvc.perform(get("/api/admin/usuarios").cookie(verificado.getResponse().getCookie("studiy_at"))).andExpect(status().isOk());
    }

    @Test
    void segredoMfaNaoPodeSerReconfiguradoDepoisDeAtivo() throws Exception {
        Sessao s = promoverAdmin();
        String segredo = ler(mvc.perform(s.em(post("/api/auth/mfa/configurar"))).andReturn()).path("segredo").asText();
        codigo(s, "ativar", codigoAtual(segredo, 0));
        mvc.perform(s.em(post("/api/auth/mfa/configurar"))).andExpect(status().isConflict());
    }
}
