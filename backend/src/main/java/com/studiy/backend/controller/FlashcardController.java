package com.studiy.backend.controller;

import com.studiy.backend.dto.FlashcardRequest;
import com.studiy.backend.dto.FlashcardResponse;
import com.studiy.backend.dto.RevisarFlashcardRequest;
import com.studiy.backend.security.UsuarioAtual;
import com.studiy.backend.service.FlashcardService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/materias/{materiaId}/flashcards")
public class FlashcardController {

    private final FlashcardService flashcardService;
    private final UsuarioAtual usuarioAtual;

    public FlashcardController(FlashcardService flashcardService, UsuarioAtual usuarioAtual) {
        this.flashcardService = flashcardService;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    public List<FlashcardResponse> listarTodos(Authentication auth, @PathVariable Long materiaId) {
        return flashcardService.listarTodos(usuarioAtual.resolver(auth), materiaId);
    }

    /** Fila de revisão do dia (repetição espaçada - SM-2). */
    @GetMapping("/revisao")
    public com.studiy.backend.dto.RevisaoResponse listarParaRevisar(Authentication auth, @PathVariable Long materiaId) {
        return com.studiy.backend.dto.RevisaoResponse.de(flashcardService.listarParaRevisar(usuarioAtual.resolver(auth), materiaId));
    }

    @PutMapping("/{flashcardId}")
    public FlashcardResponse atualizar(Authentication auth, @PathVariable Long materiaId, @PathVariable Long flashcardId, @Valid @RequestBody FlashcardRequest request) {
        return flashcardService.atualizar(usuarioAtual.resolver(auth), materiaId, flashcardId, request);
    }

    @DeleteMapping("/{flashcardId}")
    public void deletar(Authentication auth, @PathVariable Long materiaId, @PathVariable Long flashcardId) {
        flashcardService.deletar(usuarioAtual.resolver(auth), materiaId, flashcardId);
    }

    @PostMapping("/{flashcardId}/revisar")
    public FlashcardResponse revisar(Authentication auth, @PathVariable Long materiaId, @PathVariable Long flashcardId, @Valid @RequestBody RevisarFlashcardRequest request) {
        return flashcardService.revisar(usuarioAtual.resolver(auth), materiaId, flashcardId, request);
    }
}
