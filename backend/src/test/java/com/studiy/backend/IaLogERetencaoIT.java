package com.studiy.backend;

import com.studiy.backend.model.IaChamada;
import com.studiy.backend.model.RevisaoLog;
import com.studiy.backend.model.SessaoRefresh;
import com.studiy.backend.model.TipoGeracao;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.IaChamadaRepository;
import com.studiy.backend.repository.RevisaoLogRepository;
import com.studiy.backend.repository.SessaoRefreshRepository;
import com.studiy.backend.service.RetencaoService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IaLogERetencaoIT extends IntegracaoBase {

    @Autowired IaChamadaRepository iaChamadas;
    @Autowired SessaoRefreshRepository sessoes;
    @Autowired RetencaoService retencao;
    @Autowired RevisaoLogRepository revisoesLog;

    static byte[] pdfComTexto(String texto) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(texto);
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Cria matéria + PDF e devolve [materiaId, pdfId]. */
    protected long[] materiaComPdf(Sessao s) throws Exception {
        long m = ler(mvc.perform(s.em(post("/api/materias")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", "IA", "cor", "azul")))).andReturn()).path("id").asLong();
        MockMultipartFile f = new MockMultipartFile("arquivo", "aula.pdf", "application/pdf",
                pdfComTexto("Fotossintese converte luz em energia quimica nas plantas."));
        long pdf = ler(mvc.perform(s.em(multipart("/api/materias/" + m + "/pdfs").file(f)))
                .andExpect(status().isOk()).andReturn()).path("id").asLong();
        return new long[]{m, pdf};
    }

    @Test
    void cadaGeracaoRegistraTokensECusto() throws Exception {
        Sessao s = novaSessao();
        long[] ids = materiaComPdf(s);
        long antes = iaChamadas.count();

        mvc.perform(s.em(post("/api/materias/" + ids[0] + "/gerar")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(Map.of("pdfId", ids[1], "tipo", "RESUMO"))))
                .andExpect(status().isOk());

        assertThat(iaChamadas.count()).isEqualTo(antes + 1);
        IaChamada c = iaChamadas.findAll().stream().max(java.util.Comparator.comparing(IaChamada::getId)).orElseThrow();
        assertThat(c.getTipo()).isEqualTo(TipoGeracao.RESUMO);
        assertThat(c.isSucesso()).isTrue();
        assertThat(c.getModelo()).isEqualTo("mock"); // sem ANTHROPIC_API_KEY nos testes
    }

    @Test
    void retencaoApagaRefreshVencidoTokensExpiradosELogAntigo() throws Exception {
        Sessao s = novaSessao();
        Usuario u = usuarios.findByEmail(s.email()).orElseThrow();
        u.setResetTokenHash("a".repeat(64));
        u.setResetTokenExpira(LocalDateTime.now().minusMinutes(1));
        usuarios.save(u);
        SessaoRefresh vencida = sessoes.save(SessaoRefresh.builder().usuario(u).tokenHash(UUID.randomUUID().toString().replace("-", ""))
                .familia(UUID.randomUUID().toString()).criadoEm(LocalDateTime.now().minusDays(30))
                .expiraEm(LocalDateTime.now().minusDays(1)).build());
        IaChamada antiga = iaChamadas.save(IaChamada.builder().usuario(u).tipo(TipoGeracao.RESUMO).modelo("m")
                .sucesso(true).criadoEm(LocalDateTime.now().minusDays(400)).build());
        IaChamada recente = iaChamadas.save(IaChamada.builder().usuario(u).tipo(TipoGeracao.RESUMO).modelo("m")
                .sucesso(true).criadoEm(LocalDateTime.now()).build());
        RevisaoLog revisaoAntiga = revisoesLog.save(new RevisaoLog(u.getId(), LocalDateTime.now().minusDays(120)));
        RevisaoLog revisaoRecente = revisoesLog.save(new RevisaoLog(u.getId(), LocalDateTime.now().minusDays(3)));

        retencao.limpar();

        assertThat(sessoes.findById(vencida.getId())).isEmpty();
        assertThat(iaChamadas.findById(antiga.getId())).isEmpty();
        assertThat(iaChamadas.findById(recente.getId())).isPresent();
        assertThat(revisoesLog.findById(revisaoAntiga.getId())).isEmpty();
        assertThat(revisoesLog.findById(revisaoRecente.getId())).isPresent();
        assertThat(usuarios.findByEmail(s.email()).orElseThrow().getResetTokenHash()).isNull();
        // a sessão válida do login continua funcionando
        assertThat(sessoes.count()).isPositive();
    }
}
