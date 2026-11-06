package com.studiy.backend;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contexto completo sobe com o Postgres real e todas as migrations Flyway aplicadas (ddl-auto=validate). */
class ContextoIT extends IntegracaoBase {

    @Test
    void contextoSobeEMigrationsBatemComAsEntidades() throws Exception {
        mvc.perform(get("/api/saude")).andExpect(status().isOk());
    }

    @Test
    void semCredencialNaoAcessaNada() throws Exception {
        mvc.perform(get("/api/materias")).andExpect(status().isUnauthorized());
    }
}
