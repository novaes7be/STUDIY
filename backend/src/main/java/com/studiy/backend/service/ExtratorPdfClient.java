package com.studiy.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cliente do extrator Python (pdf-extrator, PyMuPDF): PDF -> Markdown com figuras.
 * Qualquer falha (fora do ar, lento, PDF que ele não leu) devolve vazio e quem chama usa o
 * conversor PDFBox. O upload nunca depende do extrator para funcionar.
 */
@Service
public class ExtratorPdfClient {

    private static final Logger log = LoggerFactory.getLogger(ExtratorPdfClient.class);

    private final RestClient rest;
    private final String token;
    private final boolean ativo;

    public ExtratorPdfClient(@Value("${studiy.extrator.url:}") String url,
                             @Value("${studiy.extrator.token:}") String token) {
        this.ativo = url != null && !url.isBlank();
        this.token = token == null ? "" : token;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(3_000);
        fabrica.setReadTimeout(150_000); // o extrator desiste em 120 s
        this.rest = RestClient.builder().baseUrl(ativo ? url : "http://desligado").requestFactory(fabrica).build();
    }

    public record Figura(int numero, String mime, int largura, int altura, String base64) {}

    public record Extraido(String markdown, List<Figura> figuras, String motor) {}

    public Optional<Extraido> converter(byte[] pdf) {
        if (!ativo) return Optional.empty();
        try {
            MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
            partes.add("arquivo", new ByteArrayResource(pdf) {
                @Override
                public String getFilename() {
                    return "documento.pdf"; // o nome real não sai da API
                }
            });
            JsonNode r = rest.post().uri("/converter")
                    .header("X-Extrator-Token", token)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(partes)
                    .retrieve()
                    .body(JsonNode.class);
            if (r == null || !r.path("markdown").isTextual()) return Optional.empty();
            List<Figura> figuras = new ArrayList<>();
            for (JsonNode i : r.path("imagens")) {
                String mime = i.path("mime").asText();
                if (!mime.matches("image/(png|jpeg|webp|gif)")) continue; // só o que a API de IA aceita
                figuras.add(new Figura(i.path("id").asInt(), mime, i.path("largura").asInt(),
                        i.path("altura").asInt(), i.path("base64").asText()));
            }
            return Optional.of(new Extraido(r.path("markdown").asText(), figuras, r.path("motor").asText("pymupdf4llm")));
        } catch (Exception e) {
            log.warn("Extrator de PDF indisponível ou falhou ({}); usando PDFBox", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
