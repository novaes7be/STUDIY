package com.studiy.backend;

import com.studiy.backend.repository.FlashcardRepository;
import com.studiy.backend.repository.IaChamadaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mesmo conteúdo gerado de novo: não chama a IA, não gasta limite, não dá XP e não duplica cartões. */
class ReaproveitamentoIT extends IntegracaoBase {

    private static final String TEXTO = "Mitose divide a celula em duas celulas filhas identicas.";

    @Autowired IaChamadaRepository iaChamadas;
    @Autowired FlashcardRepository flashcards;

    private long materia(Sessao s) throws Exception {
        return ler(mvc.perform(s.em(post("/api/materias")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", "Bio", "cor", "verde")))).andReturn()).path("id").asLong();
    }

    private long pdf(Sessao s, long materia, String nome) throws Exception {
        MockMultipartFile f = new MockMultipartFile("arquivo", nome, "application/pdf", IaLogERetencaoIT.pdfComTexto(TEXTO));
        return ler(mvc.perform(s.em(multipart("/api/materias/" + materia + "/pdfs").file(f)))
                .andExpect(status().isOk()).andReturn()).path("id").asLong();
    }

    private boolean gerar(Sessao s, long materia, long pdf) throws Exception {
        return ler(mvc.perform(s.em(post("/api/materias/" + materia + "/gerar")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("pdfId", pdf, "tipo", "FLASHCARD"))))
                .andExpect(status().isOk()).andReturn()).path("reaproveitado").asBoolean();
    }

    private int usados(Sessao s) throws Exception {
        return ler(mvc.perform(s.em(get("/api/usuarios/me"))).andReturn()).path("pdfsUsados").asInt();
    }

    @Test
    void mesmoPdfOuMesmoConteudoReaproveitaSemDuplicar() throws Exception {
        Sessao s = novaSessao();
        long m = materia(s);
        long p1 = pdf(s, m, "aula.pdf");
        long chamadasAntes = iaChamadas.count();

        assertThat(gerar(s, m, p1)).isFalse();
        int cartoes = flashcards.findByMateriaId(m).size();
        int usadosDepoisDaPrimeira = usados(s);
        assertThat(cartoes).isPositive();

        assertThat(gerar(s, m, p1)).isTrue();                         // mesmo PDF
        assertThat(gerar(s, m, pdf(s, m, "copia.pdf"))).isTrue();     // outro upload, mesmo conteúdo
        assertThat(flashcards.findByMateriaId(m)).hasSize(cartoes);    // nada duplicado
        assertThat(usados(s)).isEqualTo(usadosDepoisDaPrimeira);       // limite intacto
        assertThat(iaChamadas.count()).isEqualTo(chamadasAntes + 1);   // IA só na primeira

        long outra = materia(s);                                       // outra matéria recebe os cartões
        assertThat(gerar(s, outra, pdf(s, outra, "aula.pdf"))).isTrue();
        assertThat(flashcards.findByMateriaId(outra)).hasSize(cartoes);
    }

    @Test
    void reaproveitamentoNaoVazaEntreUsuarios() throws Exception {
        Sessao ana = novaSessao();
        long mAna = materia(ana);
        gerar(ana, mAna, pdf(ana, mAna, "aula.pdf"));

        Sessao beto = novaSessao();
        long mBeto = materia(beto);
        assertThat(gerar(beto, mBeto, pdf(beto, mBeto, "aula.pdf"))).isFalse(); // cache é por usuário
    }
}
