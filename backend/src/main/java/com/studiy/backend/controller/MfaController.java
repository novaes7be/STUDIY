package com.studiy.backend.controller;

import com.studiy.backend.dto.CodigoMfaRequest;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.security.SessaoService;
import com.studiy.backend.security.UsuarioAtual;
import com.studiy.backend.service.MfaService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Segundo fator para administradores. Exige sessão (senha) válida; ativar/verificar elevam a sessão. */
@RestController
@RequestMapping("/api/auth/mfa")
public class MfaController {

    private final MfaService mfaService;
    private final SessaoService sessaoService;
    private final UsuarioAtual usuarioAtual;

    public MfaController(MfaService mfaService, SessaoService sessaoService, UsuarioAtual usuarioAtual) {
        this.mfaService = mfaService;
        this.sessaoService = sessaoService;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping("/configurar")
    public MfaService.ConfigurarResponse configurar(Authentication auth) {
        return mfaService.configurar(usuarioAtual.resolver(auth));
    }

    @PostMapping("/ativar")
    public void ativar(Authentication auth, @Valid @RequestBody CodigoMfaRequest req, HttpServletResponse res) {
        Usuario u = usuarioAtual.resolver(auth);
        mfaService.ativar(u, req.codigo());
        sessaoService.emitirComMfa(u, res);
    }

    @PostMapping("/verificar")
    public void verificar(Authentication auth, @Valid @RequestBody CodigoMfaRequest req, HttpServletResponse res) {
        Usuario u = usuarioAtual.resolver(auth);
        mfaService.verificar(u, req.codigo());
        sessaoService.emitirComMfa(u, res);
    }
}
