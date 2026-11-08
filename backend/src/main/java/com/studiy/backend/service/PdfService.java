package com.studiy.backend.service;

import com.studiy.backend.dto.PdfResponse;
import com.studiy.backend.model.Materia;
import com.studiy.backend.model.Pdf;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.PdfRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import com.studiy.backend.security.CryptoService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    public PdfService(PdfRepository pdfRepository, MateriaService materiaService, CryptoService crypto) {
        this.pdfRepository = pdfRepository;
        this.materiaService = materiaService;
        this.crypto = crypto;
    }

    /** Impressão digital do conteúdo; PDFs anteriores a ela ganham uma na primeira geração. */
    public String impressaoDigital(Pdf pdf) {
        if (pdf.getHashConteudo() == null) {
            pdf.setHashConteudo(crypto.impressaoDigital(pdf.getTextoExtraido()));
            pdfRepository.save(pdf);
        }
        return pdf.getHashConteudo();
    }

    public Pdf upload(Usuario usuario, Long materiaId, MultipartFile arquivo) {
        Materia materia = materiaService.buscarDoUsuario(usuario, materiaId);
        byte[] bytes = lerEValidar(arquivo);
        String texto = extrairTexto(bytes);

        if (texto.trim().length() < TEXTO_MINIMO_CARACTERES) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Não foi possível extrair texto desse PDF. Ele pode estar vazio ou ser escaneado (sem texto selecionável).");
        }
        if (texto.length() > TEXTO_MAXIMO_CARACTERES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Esse PDF tem texto demais para processar de uma vez. Divida em partes menores (por capítulo, por exemplo).");
        }

        return pdfRepository.save(Pdf.builder()
                .nomeArquivo(nomeSeguro(arquivo.getOriginalFilename()))
                .textoExtraido(texto)
                .hashConteudo(crypto.impressaoDigital(texto))
                .materia(materia)
                .build());
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

    /** Markdown do PDF para download (.md). Só dentro de uma matéria do usuário. */
    public Markdown markdown(Usuario usuario, Long materiaId, Long pdfId) {
        materiaService.buscarDoUsuario(usuario, materiaId);
        Pdf pdf = buscarDaMateria(materiaId, pdfId);
        String nome = pdf.getNomeArquivo().replaceFirst("(?i)\\.pdf$", "") + ".md";
        return new Markdown(nome, pdf.getTextoExtraido());
    }

    public record Markdown(String nomeArquivo, String conteudo) {}

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
