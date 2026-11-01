package com.studiy.backend.controller;

import com.studiy.backend.dto.GerarRequest;
import com.studiy.backend.dto.GerarResponse;
import com.studiy.backend.model.Materia;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.security.UsuarioAtual;
import com.studiy.backend.service.GeracaoService;
import com.studiy.backend.service.MateriaService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/materias/{materiaId}/gerar")
public class GeracaoController {

    private final GeracaoService geracaoService;
    private final MateriaService materiaService;
    private final UsuarioAtual usuarioAtual;

    public GeracaoController(GeracaoService geracaoService, MateriaService materiaService, UsuarioAtual usuarioAtual) {
        this.geracaoService = geracaoService;
        this.materiaService = materiaService;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping
    public GerarResponse gerar(Authentication auth, @PathVariable Long materiaId, @Valid @RequestBody GerarRequest request) {
        Usuario usuario = usuarioAtual.resolver(auth);
        Materia materia = materiaService.buscarDoUsuario(usuario, materiaId);
        return geracaoService.gerar(usuario, materia, request);
    }
}
