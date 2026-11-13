package com.studiy.backend;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressão de capacidade: o upload espera o extrator por segundos (ou ~2 min em PDF grande) e NÃO pode
 * segurar conexão do banco enquanto espera. Medido na VPS: com o pool de 10 e a conversão dentro da transação,
 * dez uploads paralisavam o sistema (active=10, waiting=189, 1.080 erros 500). Aqui o pool tem só 3 conexões.
 */
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.connection-timeout=30000"})
class UploadNaoSegraConexaoIT extends IntegracaoBase {

    private static final long ATRASO_DO_EXTRATOR_MS = 4_000;
    private static final HttpServer servidor = iniciar();

    private static HttpServer iniciar() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.setExecutor(Executors.newFixedThreadPool(12));   // o padrão atende um por vez e mascararia o teste
            s.createContext("/converter", troca -> {
                try {
                    troca.getRequestBody().readAllBytes();
                    Thread.sleep(ATRASO_DO_EXTRATOR_MS);
                    byte[] corpo = ("{\"markdown\": \"# Aula\\n\\nConteudo suficiente para passar da validacao de tamanho minimo.\\n\","
                            + " \"paginas\": 1, \"motor\": \"falso\", \"imagensOmitidas\": 0, \"imagens\": []}")
                            .getBytes(StandardCharsets.UTF_8);
                    troca.getResponseHeaders().add("Content-Type", "application/json");
                    troca.sendResponseHeaders(200, corpo.length);
                    troca.getResponseBody().write(corpo);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    troca.close();
                }
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
        r.add("studiy.extrator.token", () -> "token-do-extrator-de-teste");
    }

    @AfterAll
    static void parar() {
        servidor.stop(0);
    }

    @Test
    void rotaLeveRespondeEnquantoVariosUploadsEsperamOExtrator() throws Exception {
        int uploads = 6;                               // o dobro do pool: com conexão presa, 3 ficariam esperando
        List<Sessao> sessoes = new ArrayList<>();
        List<Long> materias = new ArrayList<>();
        for (int i = 0; i < uploads; i++) {
            Sessao s = novaSessao();
            sessoes.add(s);
            materias.add(ler(mvc.perform(s.em(post("/api/materias")).contentType(MediaType.APPLICATION_JSON)
                    .content(corpo(Map.of("nome", "Aula " + i, "cor", "verde")))).andReturn()).path("id").asLong());
        }
        Sessao observador = novaSessao();

        ExecutorService pool = Executors.newFixedThreadPool(uploads);
        try {
            List<Future<Integer>> envios = new ArrayList<>();
            for (int i = 0; i < uploads; i++) {
                final int n = i;
                Callable<Integer> envio = () -> mvc.perform(sessoes.get(n).em(multipart("/api/materias/" + materias.get(n) + "/pdfs")
                                .file(new MockMultipartFile("arquivo", "aula.pdf", "application/pdf",
                                        IaLogERetencaoIT.pdfComTexto("Conteudo da aula numero " + n + " sobre redes de computadores.")))))
                        .andReturn().getResponse().getStatus();
                envios.add(pool.submit(envio));
            }
            Thread.sleep(1_000);                       // todos os uploads já estão esperando o extrator

            long inicio = System.nanoTime();
            mvc.perform(observador.em(get("/api/usuarios/me"))).andExpect(status().isOk());
            long ms = (System.nanoTime() - inicio) / 1_000_000;

            assertThat(ms).as("GET /me durante %d uploads lentos (pool de 3 conexões)", uploads)
                    .isLessThan(ATRASO_DO_EXTRATOR_MS / 2);
            for (Future<Integer> f : envios) {
                assertThat(f.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
