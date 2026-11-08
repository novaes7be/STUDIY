package com.studiy.backend.service;

import com.studiy.backend.dto.GerarRequest;
import com.studiy.backend.dto.GerarResponse;
import com.studiy.backend.model.*;
import com.studiy.backend.repository.FlashcardRepository;
import com.studiy.backend.repository.GeracaoCacheRepository;
import com.studiy.backend.repository.ResumoRepository;
import com.studiy.backend.repository.SimuladoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orquestra uma geração de IA: reaproveita se o mesmo conteúdo já foi gerado -> checa limite
 * -> chama IA -> incrementa uso -> persiste o resultado -> retorna pro front.
 */
@Service
public class GeracaoService {

    private final UsoMensalService usoMensalService;
    private final AnthropicService anthropicService;
    private final GeradorConteudo geradorConteudo;
    private final PdfService pdfService;
    private final FlashcardRepository flashcardRepository;
    private final SimuladoRepository simuladoRepository;
    private final ResumoRepository resumoRepository;
    private final GeracaoCacheRepository cacheRepository;
    private final GamificacaoService gamificacaoService;
    private final IaCustoService iaCustoService;

    private static final Pattern FLASHCARD_PATTERN =
            Pattern.compile("Pergunta:\\s*(.+?)\\s*\\|\\s*Resposta:\\s*(.+)");

    public GeracaoService(
            UsoMensalService usoMensalService,
            AnthropicService anthropicService,
            GeradorConteudo geradorConteudo,
            PdfService pdfService,
            FlashcardRepository flashcardRepository,
            SimuladoRepository simuladoRepository,
            ResumoRepository resumoRepository,
            GeracaoCacheRepository cacheRepository,
            GamificacaoService gamificacaoService,
            IaCustoService iaCustoService
    ) {
        this.iaCustoService = iaCustoService;
        this.usoMensalService = usoMensalService;
        this.anthropicService = anthropicService;
        this.geradorConteudo = geradorConteudo;
        this.pdfService = pdfService;
        this.flashcardRepository = flashcardRepository;
        this.simuladoRepository = simuladoRepository;
        this.resumoRepository = resumoRepository;
        this.cacheRepository = cacheRepository;
        this.gamificacaoService = gamificacaoService;
    }

    public GerarResponse gerar(Usuario usuario, Materia materia, GerarRequest request) {
        AuthService.exigirEmailVerificado(usuario);

        // O PDF precisa pertencer a ESTA matéria (que já foi validada como do usuário): evita acesso cruzado por ID.
        Pdf pdf = pdfService.buscarDaMateria(materia.getId(), request.pdfId());
        TipoGeracao tipo = request.tipo();
        String hash = pdfService.impressaoDigital(pdf);

        // Mesmo conteúdo já gerado por este usuário: devolve sem IA, sem gastar limite e sem XP
        // (senão gerar de novo o mesmo PDF viraria um jeito de farmar XP).
        Optional<GeracaoCache> anterior = cacheRepository.findByUsuarioIdAndHashConteudoAndTipo(usuario.getId(), hash, tipo);
        if (anterior.isPresent()) {
            String conteudo = anterior.get().getConteudo();
            persistirSeFaltar(materia, pdf, tipo, conteudo, hash);
            return new GerarResponse(conteudo, true);
        }

        usoMensalService.garantirDentroDoLimite(usuario);
        GeradorConteudo.Resultado resultado;
        try {
            Map<Integer, AnthropicService.Figura> figuras = pdfService.figuras(pdf).stream().collect(Collectors.toMap(
                    PdfImagem::getNumero, f -> new AnthropicService.Figura(f.getNumero(), f.getMime(), f.getDados())));
            resultado = geradorConteudo.gerar(tipo, pdf.getTextoExtraido(), figuras);
        } catch (GeradorConteudo.FalhaNaGeracao e) {
            // as partes que responderam (e recusas) foram cobradas: o custo fica registrado
            e.cobradas().forEach(r -> iaCustoService.registrar(usuario, tipo, r));
            if (!(e.causa() instanceof AnthropicService.IaRecusouException)) {
                iaCustoService.registrarFalha(usuario, tipo, anthropicService.modeloPara(tipo), "erro");
            }
            throw e.causa();
        }
        // PDF longo = várias chamadas, cada uma com seu custo; o limite do mês conta uma geração só.
        resultado.chamadas().forEach(r -> iaCustoService.registrar(usuario, tipo, r));
        String conteudoGerado = resultado.texto();

        usoMensalService.incrementar(usuario);
        persistirSeFaltar(materia, pdf, tipo, conteudoGerado, hash);
        guardarNoCache(usuario, hash, tipo, conteudoGerado);
        // Simulado: a nota real só existe depois que o aluno responde as
        // questões geradas (fica pro endpoint de "responder simulado").

        gamificacaoService.registrarEstudo(usuario, GamificacaoService.XP_POR_GERACAO);

        return new GerarResponse(conteudoGerado, false);
    }

    /** Salva resumo/flashcards na matéria, a não ser que ela já tenha os deste mesmo conteúdo. */
    private void persistirSeFaltar(Materia materia, Pdf pdf, TipoGeracao tipo, String conteudo, String hash) {
        if (tipo == TipoGeracao.FLASHCARD && !flashcardRepository.existsByMateriaIdAndPdfHashConteudo(materia.getId(), hash)) {
            salvarFlashcards(materia, pdf, conteudo);
        } else if (tipo == TipoGeracao.RESUMO && !resumoRepository.existsByMateriaIdAndPdfHashConteudo(materia.getId(), hash)) {
            resumoRepository.save(Resumo.builder().conteudo(conteudo).materia(materia).pdf(pdf).build());
        }
    }

    private void guardarNoCache(Usuario usuario, String hash, TipoGeracao tipo, String conteudo) {
        try {
            cacheRepository.save(new GeracaoCache(usuario.getId(), hash, tipo, conteudo));
        } catch (DataIntegrityViolationException e) {
            // duas gerações iguais ao mesmo tempo (clique duplo): a primeira já guardou
        }
    }

    private void salvarFlashcards(Materia materia, Pdf pdf, String conteudo) {
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
                                        .pdf(pdf)
                                        .revisado(false)
                                        .build()
                        );
                    }
                });
    }
}
