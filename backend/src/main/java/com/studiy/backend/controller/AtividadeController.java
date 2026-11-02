package com.studiy.backend.controller;

import com.studiy.backend.dto.AtividadeRequest;
import com.studiy.backend.dto.AtividadeResponse;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.security.UsuarioAtual;
import com.studiy.backend.service.AtividadeService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/materias/{materiaId}/atividades")
public class AtividadeController {

    private final AtividadeService atividadeService;
    private final UsuarioAtual usuarioAtual;

    public AtividadeController(AtividadeService atividadeService, UsuarioAtual usuarioAtual) {
        this.atividadeService = atividadeService;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    public List<AtividadeResponse> listar(Authentication auth, @PathVariable Long materiaId) {
        return atividadeService.listar(usuarioAtual.resolver(auth), materiaId);
    }

    @PostMapping
    public AtividadeResponse criar(Authentication auth, @PathVariable Long materiaId, @Valid @RequestBody AtividadeRequest request) {
        return atividadeService.criar(usuarioAtual.resolver(auth), materiaId, request);
    }

    @PatchMapping("/{atividadeId}/concluir")
    public AtividadeResponse concluir(Authentication auth, @PathVariable Long materiaId, @PathVariable Long atividadeId) {
        return atividadeService.concluir(usuarioAtual.resolver(auth), materiaId, atividadeId);
    }

    @DeleteMapping("/{atividadeId}")
    public void deletar(Authentication auth, @PathVariable Long materiaId, @PathVariable Long atividadeId) {
        atividadeService.deletar(usuarioAtual.resolver(auth), materiaId, atividadeId);
    }
}
