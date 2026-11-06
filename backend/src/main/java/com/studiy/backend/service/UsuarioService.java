package com.studiy.backend.service;

import com.studiy.backend.dto.PerfilResponse;
import com.studiy.backend.model.Materia;
import com.studiy.backend.model.Plano;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.FlashcardRepository;
import com.studiy.backend.repository.MateriaRepository;
import com.studiy.backend.repository.PdfRepository;
import com.studiy.backend.repository.ResumoRepository;
import com.studiy.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Perfil, edição de conta e direitos do titular (LGPD art. 18): acesso/portabilidade e eliminação. */
@Service
public class UsuarioService {

    private static final Pattern AVATAR = Pattern.compile("^data:image/(png|jpeg|webp);base64,([A-Za-z0-9+/]+={0,2})$");
    private static final int AVATAR_MAX_BYTES = 150 * 1024;

    private final UsuarioRepository usuarioRepository;
    private final MateriaRepository materiaRepository;
    private final PdfRepository pdfRepository;
    private final ResumoRepository resumoRepository;
    private final FlashcardRepository flashcardRepository;
    private final UsoMensalService usoMensalService;
    private final GamificacaoService gamificacaoService;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public UsuarioService(
            UsuarioRepository usuarioRepository, MateriaRepository materiaRepository, PdfRepository pdfRepository,
            ResumoRepository resumoRepository, FlashcardRepository flashcardRepository,
            UsoMensalService usoMensalService, GamificacaoService gamificacaoService,
            PasswordEncoder passwordEncoder, AuthService authService
    ) {
        this.authService = authService;
        this.usuarioRepository = usuarioRepository;
        this.materiaRepository = materiaRepository;
        this.pdfRepository = pdfRepository;
        this.resumoRepository = resumoRepository;
        this.flashcardRepository = flashcardRepository;
        this.usoMensalService = usoMensalService;
        this.gamificacaoService = gamificacaoService;
        this.passwordEncoder = passwordEncoder;
    }

    private static final String REGRA_USO =
            "Cada resumo ou conjunto de flashcards conta como 1 geração do mês. Enviar PDFs e revisar cartões é ilimitado.";

    public PerfilResponse perfil(Usuario u) {
        Plano plano = u.planoEfetivo();
        boolean pro = plano == Plano.PREMIUM;
        int usados = usoMensalService.buscarOuCriar(u).getPdfsUsados();
        int limite = usoMensalService.limiteDoPlano(plano);
        int percentual = limite <= 0 ? 100 : Math.min(100, Math.round(100f * usados / limite));
        return new PerfilResponse(
                u.getNome(), u.getEmail(), plano,
                pro ? "Plano Pro" : "Plano Grátis",
                pro ? "Renovar" : "Assinar Pro",
                pro ? u.getPlanoExpiraEm() : null,
                usados, limite, percentual, REGRA_USO,
                gamificacaoService.status(u),
                u.getAvatar(),
                u.isEmailVerificado(),
                !u.isOnboardingConcluido());
    }

    public PerfilResponse concluirOnboarding(Usuario u) {
        u.setOnboardingConcluido(true);
        usuarioRepository.save(u);
        return perfil(u);
    }

    /* ---------------- Edição de conta ---------------- */

    public PerfilResponse atualizarNome(Usuario u, String nome) {
        u.setNome(nome.trim());
        usuarioRepository.save(u);
        return perfil(u);
    }

    /** Exige a senha atual. Derruba as sessões (o e-mail é a identidade do token); o controller emite uma nova. */
    public Usuario alterarEmail(Usuario u, String novoEmail, String senha) {
        exigirSenha(u, senha);
        String email = novoEmail.trim().toLowerCase(Locale.ROOT);
        if (!email.equals(u.getEmail())) {
            if (usuarioRepository.existsByEmail(email)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Não foi possível usar esse e-mail.");
            }
            u.setEmail(email);
            u.setEmailVerificado(false); // o endereço novo também precisa ser confirmado
            authService.revogarSessoes(u);
            authService.enviarVerificacao(u);
        }
        return u;
    }

    /** Exige a senha atual e derruba todas as sessões; o controller emite uma nova para esta. */
    public Usuario alterarSenha(Usuario u, String senhaAtual, String novaSenha) {
        exigirSenha(u, senhaAtual);
        u.setSenha(passwordEncoder.encode(novaSenha));
        authService.revogarSessoes(u);
        return u;
    }

    /** Foto de perfil: só PNG/JPEG/WebP reais (confere a assinatura do arquivo), até 150 KB. Vazio remove. */
    public PerfilResponse definirAvatar(Usuario u, String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            u.setAvatar(null);
        } else {
            String valor = dataUrl.trim();
            Matcher m = AVATAR.matcher(valor);
            if (!m.matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagem inválida. Use PNG, JPEG ou WebP.");
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(m.group(2));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagem inválida.");
            }
            if (bytes.length > AVATAR_MAX_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Imagem muito grande (máximo 150 KB).");
            }
            if (!assinaturaValida(m.group(1), bytes)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo não parece ser uma imagem válida.");
            }
            u.setAvatar(valor);
        }
        usuarioRepository.save(u);
        return perfil(u);
    }

    private void exigirSenha(Usuario u, String senha) {
        if (!passwordEncoder.matches(senha, u.getSenha())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Senha incorreta");
        }
    }

    private static boolean assinaturaValida(String tipo, byte[] b) {
        if (b.length < 12) return false;
        switch (tipo) {
            case "png":
                return (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "jpeg":
                return (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "webp":
                return b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                        && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default:
                return false;
        }
    }

    /* ---------------- LGPD ---------------- */

    /** Cópia legível de tudo que o Studiy guarda sobre o usuário. */
    public Map<String, Object> exportar(Usuario u) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> conta = new LinkedHashMap<>();
        conta.put("nome", u.getNome());
        conta.put("email", u.getEmail());
        conta.put("plano", u.planoEfetivo());
        conta.put("planoExpiraEm", u.getPlanoExpiraEm());
        conta.put("criadoEm", u.getCriadoEm());
        conta.put("termosAceitosEm", u.getTermosAceitosEm());
        conta.put("termosVersao", u.getTermosVersao());
        conta.put("fotoDePerfil", u.getAvatar());
        out.put("conta", conta);

        List<Map<String, Object>> materias = new ArrayList<>();
        for (Materia m : materiaRepository.findByUsuarioId(u.getId())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nome", m.getNome());
            item.put("cor", m.getCor());
            item.put("pdfs", pdfRepository.findByMateriaId(m.getId()).stream()
                    .map(p -> Map.of("nomeArquivo", p.getNomeArquivo(), "criadoEm", p.getCriadoEm())).toList());
            item.put("resumos", resumoRepository.findByMateriaIdOrderByCriadoEmDesc(m.getId()).stream()
                    .map(r -> Map.of("conteudo", r.getConteudo(), "criadoEm", r.getCriadoEm())).toList());
            item.put("flashcards", flashcardRepository.findByMateriaId(m.getId()).stream()
                    .map(f -> Map.of("pergunta", f.getPergunta(), "resposta", f.getResposta())).toList());
            materias.add(item);
        }
        out.put("materias", materias);
        return out;
    }

    /** Elimina a conta e, por cascata no banco, matérias, PDFs, resumos, flashcards e uso. */
    public void excluirConta(Usuario u, String senha) {
        exigirSenha(u, senha);
        usuarioRepository.delete(u);
    }
}
