package com.studiy.backend.controller;

import com.studiy.backend.dto.MateriaRequest;
import com.studiy.backend.dto.MateriaResponse;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.security.UsuarioAtual;
import com.studiy.backend.service.MateriaService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/materias")
public class MateriaController {

    private final MateriaService materiaService;
    private final UsuarioAtual usuarioAtual;

    public MateriaController(MateriaService materiaService, UsuarioAtual usuarioAtual) {
        this.materiaService = materiaService;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    public List<MateriaResponse> listar(Authentication auth) {
        Usuario usuario = usuarioAtual.resolver(auth);
        return materiaService.listar(usuario);
    }

    @PostMapping
    public MateriaResponse criar(Authentication auth, @Valid @RequestBody MateriaRequest request) {
        Usuario usuario = usuarioAtual.resolver(auth);
        return materiaService.criar(usuario, request);
    }

    @PutMapping("/{id}")
    public MateriaResponse atualizar(Authentication auth, @PathVariable Long id, @Valid @RequestBody MateriaRequest request) {
        Usuario usuario = usuarioAtual.resolver(auth);
        return materiaService.atualizar(usuario, id, request);
    }

    @DeleteMapping("/{id}")
    public void deletar(Authentication auth, @PathVariable Long id) {
        Usuario usuario = usuarioAtual.resolver(auth);
        materiaService.deletar(usuario, id);
    }
}
