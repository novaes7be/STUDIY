package com.studiy.backend.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PdfMarkdownTest {

    private static final PDType1Font NORMAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font NEGRITO = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    @Test
    void pdfViraMarkdownComTitulosListasEParagrafos() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int p = 1; p <= 3; p++) {
                PDPage pagina = new PDPage();
                doc.addPage(pagina);
                try (PDPageContentStream cs = new PDPageContentStream(doc, pagina)) {
                    escrever(cs, NORMAL, 9, 50, 760, "Apostila de Biologia - Turma " + p); // cabeçalho repetido
                    escrever(cs, NEGRITO, 20, 50, 720, "Capitulo " + p + " Celulas");
                    escrever(cs, NORMAL, 11, 50, 690, "A celula e a unidade basica da vida e todo ser vivo e for-");
                    escrever(cs, NORMAL, 11, 50, 676, "mado por uma ou mais celulas que realizam suas funcoes.");
                    escrever(cs, NORMAL, 11, 50, 640, "• Membrana plasmatica");
                    escrever(cs, NORMAL, 11, 50, 626, "• Citoplasma");
                    escrever(cs, NORMAL, 9, 300, 40, String.valueOf(p)); // número de página
                }
            }
            String md = PdfMarkdown.deDocumento(doc);

            assertThat(md).contains("# Capitulo 1 Celulas", "# Capitulo 3 Celulas");
            assertThat(md).contains("todo ser vivo e formado por uma ou mais celulas"); // hífen e quebra de linha juntados
            assertThat(md).contains("- Membrana plasmatica\n- Citoplasma");
            assertThat(md).doesNotContain("Apostila de Biologia"); // cabeçalho de todas as páginas sai
            assertThat(md.lines().map(String::strip)).doesNotContain("1", "2", "3"); // números de página saem
        }
    }

    @Test
    void textoAntigoNaoPerdeNenhumaPalavra() {
        String texto = """
                INTRODUCAO
                A fotossintese acontece nos cloro-
                plastos e produz glicose e oxigenio.
                • luz
                • agua
                12
                O processo tem duas etapas: fase clara e fase escura.
                """;
        String md = PdfMarkdown.deTexto(texto);

        assertThat(md).contains("## INTRODUCAO", "cloroplastos", "- luz\n- agua");
        assertThat(palavras(md)).containsAll(palavras(texto.replace("cloro-\nplastos", "cloroplastos").replace("12", "")));
    }

    private static Set<String> palavras(String s) {
        return Arrays.stream(s.split("[^\\p{L}\\p{N}]+")).filter(w -> !w.isBlank()).collect(Collectors.toSet());
    }

    private static void escrever(PDPageContentStream cs, PDType1Font fonte, float tamanho, float x, float y, String t) throws Exception {
        cs.beginText();
        cs.setFont(fonte, tamanho);
        cs.newLineAtOffset(x, y);
        cs.showText(t);
        cs.endText();
    }
}
