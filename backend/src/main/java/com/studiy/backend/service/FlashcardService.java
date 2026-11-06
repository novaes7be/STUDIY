package com.studiy.backend.service;

import com.studiy.backend.dto.FlashcardRequest;
import com.studiy.backend.dto.FlashcardResponse;
import com.studiy.backend.dto.RevisarFlashcardRequest;
import com.studiy.backend.model.Flashcard;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.FlashcardRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * CRUD de flashcards + algoritmo SM-2 de repetição espaçada.
 * Referência: SuperMemo SM-2 (Piotr Wozniak).
 */
@Service
public class FlashcardService {

    private final FlashcardRepository flashcardRepository;
    private final MateriaService materiaService;
    private final GamificacaoService gamificacaoService;

    public FlashcardService(FlashcardRepository flashcardRepository, MateriaService materiaService, GamificacaoService gamificacaoService) {
        this.flashcardRepository = flashcardRepository;
        this.materiaService = materiaService;
        this.gamificacaoService = gamificacaoService;
    }

    public List<FlashcardResponse> listarTodos(Usuario usuario, Long materiaId) {
        materiaService.buscarDoUsuario(usuario, materiaId);
        return flashcardRepository.findByMateriaId(materiaId).stream().map(this::paraResponse).toList();
    }

    public List<FlashcardResponse> listarParaRevisar(Usuario usuario, Long materiaId) {
        materiaService.buscarDoUsuario(usuario, materiaId);
        return flashcardRepository
                .findByMateriaIdAndProximaRevisaoLessThanEqualOrderByProximaRevisaoAsc(materiaId, LocalDate.now())
                .stream().map(this::paraResponse).toList();
    }

    public FlashcardResponse atualizar(Usuario usuario, Long materiaId, Long flashcardId, FlashcardRequest request) {
        materiaService.buscarDoUsuario(usuario, materiaId);
        Flashcard f = buscar(materiaId, flashcardId);
        f.setPergunta(request.pergunta());
        f.setResposta(request.resposta());
        return paraResponse(flashcardRepository.save(f));
    }

    public void deletar(Usuario usuario, Long materiaId, Long flashcardId) {
        materiaService.buscarDoUsuario(usuario, materiaId);
        flashcardRepository.delete(buscar(materiaId, flashcardId));
    }

    /** Aplica o SM-2 a partir da qualidade da resposta (0 a 5) e dá XP ao usuário. */
    public FlashcardResponse revisar(Usuario usuario, Long materiaId, Long flashcardId, RevisarFlashcardRequest request) {
        materiaService.buscarDoUsuario(usuario, materiaId);
        Flashcard f = buscar(materiaId, flashcardId);

        int qualidade = request.resposta().qualidade();

        if (qualidade < 3) {
            f.setRepeticoes(0);
            f.setIntervaloDias(1);
        } else {
            int repeticoes = f.getRepeticoes();
            int novoIntervalo;
            if (repeticoes == 0) {
                novoIntervalo = 1;
            } else if (repeticoes == 1) {
                novoIntervalo = 6;
            } else {
                novoIntervalo = (int) Math.round(f.getIntervaloDias() * f.getFatorFacilidade());
            }
            f.setIntervaloDias(novoIntervalo);
            f.setRepeticoes(repeticoes + 1);
        }

        double novoFator = f.getFatorFacilidade() + (0.1 - (5 - qualidade) * (0.08 + (5 - qualidade) * 0.02));
        f.setFatorFacilidade(Math.max(1.3, novoFator));
        f.setProximaRevisao(LocalDate.now().plusDays(f.getIntervaloDias()));
        f.setRevisado(true);

        Flashcard salvo = flashcardRepository.save(f);
        gamificacaoService.registrarAtividade(usuario);

        return paraResponse(salvo);
    }

    private Flashcard buscar(Long materiaId, Long flashcardId) {
        return flashcardRepository.findByIdAndMateriaId(flashcardId, materiaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flashcard não encontrado"));
    }

    private FlashcardResponse paraResponse(Flashcard f) {
        return new FlashcardResponse(f.getId(), f.getPergunta(), f.getResposta(), f.getRepeticoes(), f.getProximaRevisao());
    }
}
