package com.studiy.backend.service;

import com.studiy.backend.dto.GerarRequest;
import com.studiy.backend.dto.GerarResponse;
import com.studiy.backend.model.*;
import com.studiy.backend.repository.FlashcardRepository;
import com.studiy.backend.repository.ResumoRepository;
import com.studiy.backend.repository.SimuladoRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orquestra uma geração de IA: checa limite -> chama IA -> incrementa uso
 * -> persiste o resultado (flashcards/simulado) -> retorna pro front.
 */
@Service
public class GeracaoService {

    private final UsoMensalService usoMensalService;
    private final AnthropicService anthropicService;
    private final PdfService pdfService;
    private final FlashcardRepository flashcardRepository;
    private final SimuladoRepository simuladoRepository;
    private final ResumoRepository resumoRepository;
    private final GamificacaoService gamificacaoService;

    private static final Pattern FLASHCARD_PATTERN =
            Pattern.compile("Pergunta:\\s*(.+?)\\s*\\|\\s*Resposta:\\s*(.+)");

    public GeracaoService(
            UsoMensalService usoMensalService,
            AnthropicService anthropicService,
            PdfService pdfService,
            FlashcardRepository flashcardRepository,
            SimuladoRepository simuladoRepository,
            ResumoRepository resumoRepository,
            GamificacaoService gamificacaoService
    ) {
        this.usoMensalService = usoMensalService;
        this.anthropicService = anthropicService;
        this.pdfService = pdfService;
        this.flashcardRepository = flashcardRepository;
        this.simuladoRepository = simuladoRepository;
        this.resumoRepository = resumoRepository;
        this.gamificacaoService = gamificacaoService;
    }

    public GerarResponse gerar(Usuario usuario, Materia materia, GerarRequest request) {
        AuthService.exigirEmailVerificado(usuario);
        usoMensalService.garantirDentroDoLimite(usuario);

        // O PDF precisa pertencer a ESTA matéria (que já foi validada como do usuário): evita acesso cruzado por ID.
        Pdf pdf = pdfService.buscarDaMateria(materia.getId(), request.pdfId());
        String conteudoGerado = anthropicService.gerar(request.tipo(), pdf.getTextoExtraido());

        usoMensalService.incrementar(usuario);

        if (request.tipo() == TipoGeracao.FLASHCARD) {
            salvarFlashcards(materia, conteudoGerado);
        } else if (request.tipo() == TipoGeracao.RESUMO) {
            resumoRepository.save(
                    Resumo.builder().conteudo(conteudoGerado).materia(materia).pdf(pdf).build()
            );
        }
        // Simulado: a nota real só existe depois que o aluno responde as
        // questões geradas (fica pro endpoint de "responder simulado").

        gamificacaoService.registrarAtividade(usuario);

        return new GerarResponse(conteudoGerado);
    }

    private void salvarFlashcards(Materia materia, String conteudo) {
        Arrays.stream(conteudo.split("\n"))
                .map(String::trim)
                .filter(linha -> !linha.isBlank())
                .forEach(linha -> {
                    Matcher m = FLASHCARD_PATTERN.matcher(linha);
                    if (m.find()) {
                        flashcardRepository.save(
                                Flashcard.builder()
                                        .pergunta(m.group(1).trim())
                                        .resposta(m.group(2).trim())
                                        .materia(materia)
                                        .revisado(false)
                                        .build()
                        );
                    }
                });
    }
}
