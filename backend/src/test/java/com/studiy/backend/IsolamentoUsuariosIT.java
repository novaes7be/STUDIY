package com.studiy.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolamento entre contas em TODAS as rotas com ID: a Ana cria os dados; o Beto (outra conta, verificada)
 * tenta ler/alterar/apagar por ID e recebe 404 — nunca 403, para não confirmar que o recurso existe.
 * Também cobre o caso "ID de outro usuário dentro da MINHA matéria" (flashcard/atividade/PDF cruzados).
 */
class IsolamentoUsuariosIT extends IntegracaoBase {

    private Sessao ana;
    private Sessao beto;
    private long materiaAna;
    private long pdfAna;
    private long flashcardAna;
    private long atividadeAna;
    private String pedidoAna;
    private long materiaBeto;

    private long criarMateria(Sessao s, String nome) throws Exception {
        return ler(mvc.perform(s.em(post("/api/materias")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", nome, "cor", "roxo")))).andExpect(status().isOk()).andReturn()).path("id").asLong();
    }

    @BeforeEach
    void dadosDaAna() throws Exception {
        ana = novaSessao();
        beto = novaSessao();
        materiaAna = criarMateria(ana, "Biologia da Ana");
        materiaBeto = criarMateria(beto, "Física do Beto");

        MockMultipartFile f = new MockMultipartFile("arquivo", "aula.pdf", "application/pdf",
                IaLogERetencaoIT.pdfComTexto("Mitocondria produz ATP na respiracao celular."));
        pdfAna = ler(mvc.perform(ana.em(multipart("/api/materias/" + materiaAna + "/pdfs").file(f)))
                .andExpect(status().isOk()).andReturn()).path("id").asLong();

        // flashcards via geração (IA em modo mock nos testes) e um resumo
        mvc.perform(ana.em(post("/api/materias/" + materiaAna + "/gerar")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("pdfId", pdfAna, "tipo", "FLASHCARD")))).andExpect(status().isOk());
        mvc.perform(ana.em(post("/api/materias/" + materiaAna + "/gerar")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("pdfId", pdfAna, "tipo", "RESUMO")))).andExpect(status().isOk());
        flashcardAna = ler(mvc.perform(get("/api/materias/" + materiaAna + "/flashcards").cookie(ana.acesso()))
                .andReturn()).get(0).path("id").asLong();

        atividadeAna = ler(mvc.perform(ana.em(post("/api/materias/" + materiaAna + "/atividades"))
                .contentType(MediaType.APPLICATION_JSON).content(corpo(Map.of("titulo", "Prova")))).andExpect(status().isOk())
                .andReturn()).path("id").asLong();

        pedidoAna = ler(mvc.perform(ana.em(post("/api/pagamentos/checkout")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("metodo", "PIX")))).andExpect(status().isOk()).andReturn()).path("pedidoId").asText();
    }

    private void beto404(MockHttpServletRequestBuilder req) throws Exception {
        mvc.perform(beto.em(req)).andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, Object body) throws Exception {
        return req.contentType(MediaType.APPLICATION_JSON).content(corpo(body));
    }

    @Test
    void materia() throws Exception {
        mvc.perform(get("/api/materias").cookie(beto.acesso())).andExpect(jsonPath("$", hasSize(1)));
        beto404(json(put("/api/materias/" + materiaAna), Map.of("nome", "Invadida", "cor", "azul")));
        beto404(delete("/api/materias/" + materiaAna));
    }

    @Test
    void pdf() throws Exception {
        beto404(get("/api/materias/" + materiaAna + "/pdfs"));
        MockMultipartFile f = new MockMultipartFile("arquivo", "x.pdf", "application/pdf", IaLogERetencaoIT.pdfComTexto("x"));
        beto404(multipart("/api/materias/" + materiaAna + "/pdfs").file(f));
        // PDF da Ana usado a partir da matéria do próprio Beto
        beto404(json(post("/api/materias/" + materiaBeto + "/gerar"), Map.of("pdfId", pdfAna, "tipo", "RESUMO")));
    }

    @Test
    void resumoEGeracao() throws Exception {
        beto404(get("/api/materias/" + materiaAna + "/resumos"));
        beto404(json(post("/api/materias/" + materiaAna + "/gerar"), Map.of("pdfId", pdfAna, "tipo", "RESUMO")));
    }

    @Test
    void flashcard() throws Exception {
        String base = "/api/materias/" + materiaAna + "/flashcards";
        beto404(get(base));
        beto404(get(base + "/revisao"));
        beto404(json(put(base + "/" + flashcardAna), Map.of("pergunta", "p", "resposta", "r")));
        beto404(json(post(base + "/" + flashcardAna + "/revisar"), Map.of("resposta", "BOM")));
        beto404(delete(base + "/" + flashcardAna));
        // flashcard da Ana pelo caminho da matéria do Beto
        String cruzado = "/api/materias/" + materiaBeto + "/flashcards/" + flashcardAna;
        beto404(json(put(cruzado), Map.of("pergunta", "p", "resposta", "r")));
        beto404(json(post(cruzado + "/revisar"), Map.of("resposta", "BOM")));
        beto404(delete(cruzado));
    }

    @Test
    void atividade() throws Exception {
        String base = "/api/materias/" + materiaAna + "/atividades";
        beto404(get(base));
        beto404(json(post(base), Map.of("titulo", "Invasão")));
        beto404(patch(base + "/" + atividadeAna + "/concluir"));
        beto404(delete(base + "/" + atividadeAna));
        String cruzado = "/api/materias/" + materiaBeto + "/atividades/" + atividadeAna;
        beto404(patch(cruzado + "/concluir"));
        beto404(delete(cruzado));
    }

    @Test
    void pedido() throws Exception {
        beto404(get("/api/pagamentos/" + pedidoAna));
        beto404(post("/api/pagamentos/" + pedidoAna + "/simular")); // rota só existe em dev; mesmo assim, 404 pro Beto
    }

    @Test
    void nadaDaAnaFoiAlterado() throws Exception {
        materia(); pdf(); flashcard(); atividade(); pedido();
        mvc.perform(get("/api/materias").cookie(ana.acesso())).andExpect(jsonPath("$[0].nome").value("Biologia da Ana"));
        mvc.perform(get("/api/materias/" + materiaAna + "/pdfs").cookie(ana.acesso())).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/materias/" + materiaAna + "/atividades").cookie(ana.acesso()))
                .andExpect(jsonPath("$[0].titulo").value("Prova")).andExpect(jsonPath("$[0].concluida").value(false));
        mvc.perform(get("/api/materias/" + materiaAna + "/flashcards").cookie(ana.acesso()))
                .andExpect(jsonPath("$[0].id").value(flashcardAna));
        mvc.perform(get("/api/pagamentos/" + pedidoAna).cookie(ana.acesso())).andExpect(jsonPath("$.status").value("PENDENTE"));
    }
}
