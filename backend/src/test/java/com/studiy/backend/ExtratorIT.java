package com.studiy.backend;

import com.studiy.backend.repository.PdfImagemRepository;
import com.studiy.backend.repository.PdfRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Upload com o extrator PyMuPDF (falso, em HTTP): figuras guardadas; extrator fora do ar: PDFBox. */
class ExtratorIT extends IntegracaoBase {

    private static final String TOKEN = "token-do-extrator-de-teste";
    /** PNG 1x1 válido. */
    private static final String PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";

    private static final AtomicInteger statusResposta = new AtomicInteger(200);
    private static final AtomicReference<String> tokenRecebido = new AtomicReference<>();
    private static final HttpServer servidor = iniciar();

    private static HttpServer iniciar() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/converter", troca -> {
                tokenRecebido.set(troca.getRequestHeaders().getFirst("X-Extrator-Token"));
                troca.getRequestBody().readAllBytes();
                String json = """
                        {"markdown": "# Fotossintese\\n\\nTexto sobre a fotossintese nas plantas verdes.\\n\\n![Figura 1](imagem:1)\\n",
                         "paginas": 1, "motor": "pymupdf4llm 2.1", "imagensOmitidas": 0,
                         "imagens": [{"id": 1, "mime": "image/png", "largura": 1, "altura": 1, "base64": "%s"}]}
                        """.formatted(PNG);
                byte[] corpo = json.getBytes(StandardCharsets.UTF_8);
                troca.getResponseHeaders().add("Content-Type", "application/json");
                troca.sendResponseHeaders(statusResposta.get(), statusResposta.get() == 200 ? corpo.length : -1);
                if (statusResposta.get() == 200) troca.getResponseBody().write(corpo);
                troca.close();
            });
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void extrator(DynamicPropertyRegistry r) {
        r.add("studiy.extrator.url", () -> "http://127.0.0.1:" + servidor.getAddress().getPort());
        r.add("studiy.extrator.token", () -> TOKEN);
    }

    @AfterAll
    static void parar() {
        servidor.stop(0);
    }

    @Autowired PdfRepository pdfs;
    @Autowired PdfImagemRepository imagens;

    private long[] subir(Sessao s) throws Exception {
        long m = ler(mvc.perform(s.em(post("/api/materias")).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("nome", "Bio", "cor", "verde")))).andReturn()).path("id").asLong();
        MockMultipartFile f = new MockMultipartFile("arquivo", "aula.pdf", "application/pdf",
                IaLogERetencaoIT.pdfComTexto("Fotossintese nas plantas verdes e a clorofila."));
        long pdf = ler(mvc.perform(s.em(multipart("/api/materias/" + m + "/pdfs").file(f)))
                .andExpect(status().isOk()).andReturn()).path("id").asLong();
        return new long[]{m, pdf};
    }

    @Test
    void comExtratorAsFigurasSaoGuardadasEBaixamEmZipComOMdLimpo() throws Exception {
        statusResposta.set(200);
        Sessao s = novaSessao();
        long[] ids = subir(s);

        assertThat(tokenRecebido.get()).isEqualTo(TOKEN);
        assertThat(pdfs.findById(ids[1]).orElseThrow().getMotor()).isEqualTo("pymupdf4llm 2.1");
        assertThat(imagens.findByPdfIdOrderByNumero(ids[1])).singleElement()
                .satisfies(i -> assertThat(i.getDados()).isEqualTo(PNG));

        var resp = mvc.perform(s.em(get("/api/materias/" + ids[0] + "/pdfs/" + ids[1] + "/download")))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(resp.getContentType()).isEqualTo("application/zip");
        assertThat(resp.getHeader("Content-Disposition")).contains("attachment").contains("aula.zip");

        Map<String, byte[]> zip = abrirZip(resp.getContentAsByteArray());
        assertThat(zip.keySet()).containsExactlyInAnyOrder("aula.md", "figuras/figura-1.png");
        String md = new String(zip.get("aula.md"), StandardCharsets.UTF_8);
        assertThat(md).contains("![Figura 1](figuras/figura-1.png)")
                .doesNotContain("imagem:1").doesNotContain("data:image").doesNotContain("base64");
        assertThat(zip.get("figuras/figura-1.png")).isEqualTo(java.util.Base64.getDecoder().decode(PNG));
    }

    @Test
    void downloadDeOutraContaDa404() throws Exception {
        statusResposta.set(200);
        long[] ids = subir(novaSessao());
        Sessao intrusa = novaSessao();

        mvc.perform(intrusa.em(get("/api/materias/" + ids[0] + "/pdfs/" + ids[1] + "/download")))
                .andExpect(status().isNotFound());
    }

    private static Map<String, byte[]> abrirZip(byte[] bytes) throws IOException {
        Map<String, byte[]> arquivos = new java.util.LinkedHashMap<>();
        try (var in = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            for (var e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                arquivos.put(e.getName(), in.readAllBytes());
            }
        }
        return arquivos;
    }

    @Test
    void extratorForaDoArCaiNoPdfboxSemErroParaOUsuario() throws Exception {
        statusResposta.set(500);
        Sessao s = novaSessao();
        long[] ids = subir(s);

        assertThat(pdfs.findById(ids[1]).orElseThrow().getMotor()).isEqualTo("pdfbox");
        assertThat(imagens.findByPdfIdOrderByNumero(ids[1])).isEmpty();

        var resp = mvc.perform(s.em(get("/api/materias/" + ids[0] + "/pdfs/" + ids[1] + "/download")))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(resp.getContentType()).startsWith("text/markdown");           // sem figuras: .md puro, sem zip
        assertThat(resp.getHeader("Content-Disposition")).contains("aula.md");
        assertThat(resp.getContentAsString(StandardCharsets.UTF_8)).contains("Fotossintese");
    }
}
