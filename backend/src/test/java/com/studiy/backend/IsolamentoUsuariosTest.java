package com.studiy.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolamento entre contas: um usuário nunca enxerga nem altera dados de outro.
 * Usa um PostgreSQL real em container; é pulado sozinho se o Docker não estiver rodando.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings({"rawtypes", "unchecked"})
class IsolamentoUsuariosTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TestRestTemplate rest;

    private String registrar(String nome) {
        Map<String, Object> body = Map.of("nome", nome, "email", UUID.randomUUID() + "@teste.com",
                "senha", "senha-segura-123", "aceitoTermos", true);
        ResponseEntity<Map> r = rest.postForEntity("/api/auth/registrar", body, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) r.getBody().get("token");
    }

    private HttpEntity<Object> como(String token, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(body, h);
    }

    @Test
    void usuarioNaoVeNemAlteraMateriaDeOutro() {
        String tokenA = registrar("Ana");
        String tokenB = registrar("Beto");

        ResponseEntity<Map> criada = rest.exchange("/api/materias", HttpMethod.POST,
                como(tokenA, Map.of("nome", "Biologia", "cor", "roxo")), Map.class);
        assertThat(criada.getStatusCode().is2xxSuccessful()).isTrue();
        Object id = criada.getBody().get("id");

        ResponseEntity<List> listaB = rest.exchange("/api/materias", HttpMethod.GET, como(tokenB, null), List.class);
        assertThat(listaB.getBody()).isEmpty();

        ResponseEntity<String> put = rest.exchange("/api/materias/" + id, HttpMethod.PUT,
                como(tokenB, Map.of("nome", "Invadida", "cor", "azul")), String.class);
        assertThat(put.getStatusCode().is2xxSuccessful()).isFalse();

        ResponseEntity<String> del = rest.exchange("/api/materias/" + id, HttpMethod.DELETE, como(tokenB, null), String.class);
        assertThat(del.getStatusCode().is2xxSuccessful()).isFalse();

        ResponseEntity<List> listaA = rest.exchange("/api/materias", HttpMethod.GET, como(tokenA, null), List.class);
        assertThat(listaA.getBody()).hasSize(1);
    }

    @Test
    void semTokenNaoAcessaNada() {
        HttpStatus status = HttpStatus.valueOf(rest.getForEntity("/api/materias", String.class).getStatusCode().value());
        assertThat(status).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
