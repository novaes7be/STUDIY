package com.studiy.backend.service;

import com.studiy.backend.model.Papel;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.UsuarioRepository;
import com.studiy.backend.security.Totp;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Segundo fator (TOTP) para administradores.
 *  - configurar: gera o segredo (uma vez; trocar exige intervenção no banco, de propósito);
 *  - ativar: confirma o primeiro código e liga o MFA;
 *  - verificar: a cada login, eleva a sessão para "com MFA" (só ela recebe ROLE_ADMIN).
 * Códigos errados contam no mesmo bloqueio progressivo do login; um código não vale duas vezes.
 */
@Service
public class MfaService {

    public record ConfigurarResponse(String segredo, String uri) {}

    private final UsuarioRepository usuarioRepository;
    private final BloqueioLoginService bloqueio;

    public MfaService(UsuarioRepository usuarioRepository, BloqueioLoginService bloqueio) {
        this.usuarioRepository = usuarioRepository;
        this.bloqueio = bloqueio;
    }

    public ConfigurarResponse configurar(Usuario u) {
        exigirAdmin(u);
        if (u.isMfaAtivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O segundo fator já está ativo nesta conta.");
        }
        String segredo = Totp.novoSegredo();
        u.setMfaSegredo(segredo);
        usuarioRepository.save(u);
        return new ConfigurarResponse(segredo, Totp.uri("Studiy", u.getEmail(), segredo));
    }

    public void ativar(Usuario u, String codigo) {
        exigirAdmin(u);
        if (u.isMfaAtivo() || u.getMfaSegredo() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Configure o segundo fator antes de ativar.");
        }
        long passo = conferir(u, codigo);
        u.setMfaAtivo(true);
        u.setMfaUltimoPasso(passo);
        bloqueio.limpar(u);
        usuarioRepository.save(u);
    }

    public void verificar(Usuario u, String codigo) {
        exigirAdmin(u);
        if (!u.isMfaAtivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ative o segundo fator primeiro.");
        }
        long passo = conferir(u, codigo);
        u.setMfaUltimoPasso(passo);
        bloqueio.limpar(u);
        usuarioRepository.save(u);
    }

    private long conferir(Usuario u, String codigo) {
        if (bloqueio.bloqueado(u)) {
            throw codigoInvalido();
        }
        long passo = Totp.verificar(u.getMfaSegredo(), codigo, Instant.now());
        boolean reuso = u.getMfaUltimoPasso() != null && passo <= u.getMfaUltimoPasso();
        if (passo < 0 || reuso) {
            bloqueio.registrarFalha(u.getId());
            throw codigoInvalido();
        }
        return passo;
    }

    private static void exigirAdmin(Usuario u) {
        if (u.getPapel() != Papel.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
    }

    private static ResponseStatusException codigoInvalido() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Código inválido. Depois de várias tentativas, o acesso fica bloqueado por alguns minutos.");
    }
}
