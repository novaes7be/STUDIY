package com.studiy.backend.controller;

import com.studiy.backend.dto.ResumoResponse;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.ResumoRepository;
import com.studiy.backend.security.UsuarioAtual;
import com.studiy.backend.service.MateriaService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/materias/{materiaId}/resumos")
public class ResumoController {

    private final ResumoRepository resumoRepository;
    private final MateriaService materiaService;
    private final UsuarioAtual usuarioAtual;

    public ResumoController(ResumoRepository resumoRepository, MateriaService materiaService, UsuarioAtual usuarioAtual) {
        this.resumoRepository = resumoRepository;
        this.materiaService = materiaService;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    public List<ResumoResponse> listar(Authentication auth, @PathVariable Long materiaId) {
        Usuario usuario = usuarioAtual.resolver(auth);
        materiaService.buscarDoUsuario(usuario, materiaId); // garante posse
        return resumoRepository.findByMateriaIdOrderByCriadoEmDesc(materiaId)
                .stream()
                .map(r -> new ResumoResponse(r.getId(), r.getConteudo(), r.getCriadoEm()))
                .toList();
    }
}
