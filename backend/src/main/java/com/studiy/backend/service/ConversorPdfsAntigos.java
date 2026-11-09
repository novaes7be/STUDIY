package com.studiy.backend.service;

import com.studiy.backend.model.Pdf;
import com.studiy.backend.repository.PdfRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Na inicialização, reescreve em Markdown os PDFs extraídos antes da conversão (formato TEXTO).
 * O arquivo original não é guardado, então usa só heurísticas de texto (PdfMarkdown.deTexto).
 * Idempotente: depois de converter, o formato vira MARKDOWN e o PDF não é tocado de novo.
 */
@Component
public class ConversorPdfsAntigos implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConversorPdfsAntigos.class);

    private final PdfRepository pdfRepository;

    public ConversorPdfsAntigos(PdfRepository pdfRepository) {
        this.pdfRepository = pdfRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        int total = 0;
        List<Pdf> lote;
        while (!(lote = pdfRepository.findTop50ByFormatoNot("MARKDOWN")).isEmpty()) {
            for (Pdf pdf : lote) {
                String texto = pdf.getTextoExtraido() == null ? "" : pdf.getTextoExtraido();
                pdf.setTextoExtraido(PdfMarkdown.deTexto(texto));
                pdf.setFormato("MARKDOWN");
                pdf.setHashConteudo(null); // conteúdo mudou: a impressão digital é refeita na próxima geração
            }
            pdfRepository.saveAll(lote);
            total += lote.size();
        }
        if (total > 0) log.info("PDFs antigos convertidos para Markdown: {}", total);
    }
}
