package com.studiy.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Isolamento entre contas: um usuário nunca enxerga nem altera dados de outro (resposta 404, sem vazar existência). */
class IsolamentoUsuariosIT extends IntegracaoBase {

    @Test
    void usuarioNaoVeNemAlteraMateriaDeOutro() throws Exception {
        Sessao ana = novaSessao();
        Sessao beto = novaSessao();

        long id = ler(mvc.perform(ana.em(post("/api/materias")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("nome", "Biologia", "cor", "roxo"))))
                .andExpect(status().isOk()).andReturn()).path("id").asLong();

        mvc.perform(beto.em(get("/api/materias"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(beto.em(put("/api/materias/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("nome", "Invadida", "cor", "azul"))))
                .andExpect(status().isNotFound());
        mvc.perform(beto.em(delete("/api/materias/" + id))).andExpect(status().isNotFound());
        mvc.perform(ana.em(get("/api/materias"))).andExpect(jsonPath("$", hasSize(1)));
    }
}
