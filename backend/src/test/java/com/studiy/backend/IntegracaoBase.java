package com.studiy.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.UsuarioRepository;
import com.studiy.backend.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: PostgreSQL 16 real (Testcontainers, um container para toda a suíte),
 * Flyway aplicando as migrations de verdade e MockMvc passando por toda a cadeia do Spring Security.
 * O EmailService é substituído por um mock para os testes lerem os links enviados.
 */
@SpringBootTest(properties = {
        "studiy.ratelimit.auth-por-minuto=100000", "studiy.ratelimit.ia-por-minuto=100000",
        "studiy.ratelimit.upload-por-minuto=100000"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public abstract class IntegracaoBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UsuarioRepository usuarios;
    @MockBean protected EmailService emailService;

    protected static final String SENHA = "senha-segura-123";

    /** Sessão autenticada de um usuário de teste; aplica as credenciais em qualquer requisição. */
    public record Sessao(String email, String token) {
        public MockHttpServletRequestBuilder em(MockHttpServletRequestBuilder req) {
            return req.header("Authorization", "Bearer " + token);
        }
    }

    protected static String emailNovo() {
        return UUID.randomUUID() + "@teste.com";
    }

    protected String corpo(Object o) throws Exception {
        return json.writeValueAsString(o);
    }

    protected JsonNode ler(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    protected void cadastrar(String email) throws Exception {
        mvc.perform(post("/api/auth/registrar").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("nome", "Teste", "email", email, "senha", SENHA, "aceitoTermos", true))))
                .andExpect(status().is2xxSuccessful());
    }

    protected MvcResult tentarLogin(String email, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("email", email, "senha", senha)))).andReturn();
    }

    /** Cadastra, marca o e-mail como verificado (atalho de teste) e entra. */
    protected Sessao novaSessao() throws Exception {
        String email = emailNovo();
        cadastrar(email);
        Usuario u = usuarios.findByEmail(email).orElseThrow();
        u.setEmailVerificado(true);
        usuarios.save(u);
        MvcResult r = tentarLogin(email, SENHA);
        return new Sessao(email, ler(r).path("token").asText());
    }
}
