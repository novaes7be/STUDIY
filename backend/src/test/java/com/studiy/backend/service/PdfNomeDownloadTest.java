package com.studiy.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfNomeDownloadTest {

    @Test
    void tiraExtensaoPdfEMantemAcentosEEspacos() {
        assertThat(PdfService.nomeDoDownload("Engenharia de Software II - Aula 03.PDF")).isEqualTo("Engenharia de Software II - Aula 03");
        assertThat(PdfService.nomeDoDownload("Introdução às Redes.pdf")).isEqualTo("Introdução às Redes");
    }

    @Test
    void naoDeixaPassarSeparadorDeCaminhoNemControle() {
        assertThat(PdfService.nomeDoDownload("../../etc/passwd.pdf")).doesNotContain("/").doesNotContain("..");
        assertThat(PdfService.nomeDoDownload("a\\b\u0000c\r\nd.pdf")).doesNotContainPattern("[\\\\\\x00-\\x1f]");
    }

    @Test
    void nomeVazioOuSoPontosViraDocumento() {
        assertThat(PdfService.nomeDoDownload(null)).isEqualTo("documento");
        assertThat(PdfService.nomeDoDownload(".pdf")).isEqualTo("documento");
        assertThat(PdfService.nomeDoDownload("...")).isEqualTo("documento");
    }

    @Test
    void nomeLongoE_cortado() {
        assertThat(PdfService.nomeDoDownload("x".repeat(300) + ".pdf")).hasSize(80);
    }
}
