package com.studiy.backend.service;

import com.studiy.backend.dto.PdfResponse;
import com.studiy.backend.model.Materia;
import com.studiy.backend.model.Pdf;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.PdfRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import com.studiy.backend.model.PdfImagem;
import com.studiy.backend.repository.PdfImagemRepository;
import com.studiy.backend.security.CryptoService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Valida o upload e converte o PDF em Markdown no servidor (Apache PDFBox, sem IA) ANTES de
 * qualquer chamada de IA. O arquivo original NÃO é guardado: só o Markdown, cifrado em repouso.
 */
@Service
public class PdfService {

    private static final long TAMANHO_MAXIMO_BYTES = 10L * 1024 * 1024;
    private static final int PAGINAS_MAXIMAS = 300;
    /** Acima disso o PDF é recusado (com aviso), nunca cortado em silêncio. */
    private static final int TEXTO_MAXIMO_CARACTERES = 900_000;
    private static final int TEXTO_MINIMO_CARACTERES = 20;

    private final PdfRepository pdfRepository;
    private final MateriaService materiaService;
    private final CryptoService crypto;
    private final ExtratorPdfClient extrator;
    private final PdfImagemRepository imagemRepository;

    public PdfService(PdfRepository pdfRepository, MateriaService materiaService, CryptoService crypto,
                      ExtratorPdfClient extrator, PdfImagemRepository imagemRepository) {
        this.pdfRepository = pdfRepository;
        this.materiaService = materiaService;
        this.crypto = crypto;
        this.extrator = extrator;
        this.imagemRepository = imagemRepository;
    }

    /** Figuras do PDF (vazio nos convertidos pelo PDFBox). */
    public List<PdfImagem> figuras(Pdf pdf) {
        return imagemRepository.findByPdfIdOrderByNumero(pdf.getId());
    }

    /** Impressão digital do conteúdo; PDFs anteriores a ela ganham uma na primeira geração. */
    public String impressaoDigital(Pdf pdf) {
        if (pdf.getHashConteudo() == null) {
            pdf.setHashConteudo(crypto.impressaoDigital(pdf.getTextoExtraido()));
            pdfRepository.save(pdf);
        }
        return pdf.getHashConteudo();
    }

    @org.springframework.transaction.annotation.Transactional // PDF e figuras: tudo ou nada
    public Pdf upload(Usuario usuario, Long materiaId, MultipartFile arquivo) {
        Materia materia = materiaService.buscarDoUsuario(usuario, materiaId);
        byte[] bytes = lerEValidar(arquivo);
        // PyMuPDF (com figuras e gráficos) quando o extrator está no ar; senão PDFBox (só texto).
        var extraido = extrator.converter(bytes);
        String texto = extraido.map(ExtratorPdfClient.Extraido::markdown).orElseGet(() -> extrairTexto(bytes));
        String motor = extraido.map(ExtratorPdfClient.Extraido::motor).orElse("pdfbox");

        String soTexto = texto.replaceAll("!\\[[^\\]]*\\]\\(imagem:\\d+\\)", "");
        if (soTexto.trim().length() < TEXTO_MINIMO_CARACTERES) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Não foi possível extrair texto desse PDF. Ele pode estar vazio ou ser escaneado (sem texto selecionável).");
        }
        if (texto.length() > TEXTO_MAXIMO_CARACTERES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Esse PDF tem texto demais para processar de uma vez. Divida em partes menores (por capítulo, por exemplo).");
        }

        Pdf pdf = pdfRepository.save(Pdf.builder()
                .nomeArquivo(nomeSeguro(arquivo.getOriginalFilename()))
                .textoExtraido(texto)
                .hashConteudo(crypto.impressaoDigital(texto))
                .motor(motor)
                .materia(materia)
                .build());
        extraido.ifPresent(e -> e.figuras().forEach(f -> imagemRepository.save(
                new PdfImagem(pdf.getId(), f.numero(), f.mime(), f.largura(), f.altura(), f.base64()))));
        return pdf;
    }

    public List<PdfResponse> listar(Usuario usuario, Long materiaId) {
        materiaService.buscarDoUsuario(usuario, materiaId);
        return pdfRepository.findByMateriaIdOrderByCriadoEmDesc(materiaId).stream()
                .map(p -> new PdfResponse(p.getId(), p.getNomeArquivo(), p.getCriadoEm()))
                .toList();
    }

    /** Busca o PDF SOMENTE dentro da matéria já validada como pertencente ao usuário. */
    public Pdf buscarDaMateria(Long materiaId, Long pdfId) {
        return pdfRepository.findByIdAndMateriaId(pdfId, materiaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF não encontrado"));
    }

    /**
     * Arquivo do PDF para baixar. Sem figuras: o .md. Com figuras: um .zip com o .md e a pasta
     * {@code figuras/}, e o texto aponta para elas por caminho relativo (curto e legível; embutir as
     * imagens em Base64 gerava linhas de ~100 KB que travavam editores). Só dentro de uma matéria do usuário.
     */
    public Download download(Usuario usuario, Long materiaId, Long pdfId) {
        materiaService.buscarDoUsuario(usuario, materiaId);
        Pdf pdf = buscarDaMateria(materiaId, pdfId);
        String base = nomeDoDownload(pdf.getNomeArquivo());
        String md = pdf.getTextoExtraido();
        List<PdfImagem> figuras = figuras(pdf);
        if (figuras.isEmpty()) {
            return new Download(base + ".md", "text/markdown;charset=UTF-8", md.getBytes(StandardCharsets.UTF_8));
        }

        Map<String, byte[]> arquivos = new LinkedHashMap<>();
        for (PdfImagem f : figuras) {
            String alvo = "(imagem:" + f.getNumero() + ")";
            try {
                String caminho = "figuras/figura-" + f.getNumero() + "." + extensao(f.getMime());
                arquivos.put(caminho, Base64.getDecoder().decode(f.getDados()));
                md = md.replace(alvo, "(" + caminho + ")");
            } catch (IllegalArgumentException e) { // Base64 inválido: não derruba o download inteiro
                md = md.replace("![Figura " + f.getNumero() + "]" + alvo, "*[figura " + f.getNumero() + " indisponível]*");
            }
        }

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(saida, StandardCharsets.UTF_8)) {
            escrever(zip, base + ".md", md.getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, byte[]> a : arquivos.entrySet()) {
                escrever(zip, a.getKey(), a.getValue());
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível montar o arquivo");
        }
        return new Download(base + ".zip", "application/zip", saida.toByteArray());
    }

    private static void escrever(ZipOutputStream zip, String nome, byte[] dados) throws IOException {
        zip.putNextEntry(new ZipEntry(nome));
        zip.write(dados);
        zip.closeEntry();
    }

    private static String extensao(String mime) {
        return switch (mime == null ? "" : mime.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "png";
        };
    }

    /** Nome do arquivo baixado: sem .pdf e com tamanho limitado (o nome já foi higienizado no upload; aqui é defesa em profundidade). */
    static String nomeDoDownload(String nomeArquivo) {
        String base = nomeArquivo == null ? "" : nomeArquivo.replaceFirst("(?i)\\.pdf$", "");
        base = base.replaceAll("[^\\p{L}\\p{N} ._-]", "_").replaceAll("\\.{2,}", ".").strip();
        if (base.length() > 80) base = base.substring(0, 80).strip();
        return base.isEmpty() || base.equals(".") ? "documento" : base;
    }

    public record Download(String nomeArquivo, String tipo, byte[] bytes) {}

    private byte[] lerEValidar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nenhum arquivo enviado");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "PDF muito grande (máximo 10MB)");
        }
        byte[] bytes;
        try {
            bytes = arquivo.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo enviado");
        }
        // Não confiamos em Content-Type nem em extensão: conferimos a assinatura real do arquivo.
        boolean assinaturaPdf = bytes.length > 5 && "%PDF-".equals(new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        if (!assinaturaPdf) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Só são aceitos arquivos PDF");
        }
        return bytes;
    }

    private String extrairTexto(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDFs protegidos por senha não são suportados");
            }
            if (document.getNumberOfPages() > PAGINAS_MAXIMAS) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "PDF com páginas demais (máximo " + PAGINAS_MAXIMAS + ")");
            }
            return PdfMarkdown.deDocumento(document);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o PDF enviado");
        }
    }

    /** Remove caminhos e caracteres estranhos do nome enviado pelo cliente. */
    static String nomeSeguro(String original) {
        String nome = original == null ? "documento.pdf" : original.replace('\\', '/');
        nome = nome.substring(nome.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N} ._()\\-]", "_").trim();
        if (nome.isEmpty()) nome = "documento.pdf";
        return nome.length() > 120 ? nome.substring(0, 120) : nome;
    }
}
