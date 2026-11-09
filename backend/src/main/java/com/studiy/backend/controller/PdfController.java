package com.studiy.backend.controller;

import com.studiy.backend.model.Pdf;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.security.UsuarioAtual;
import com.studiy.backend.service.PdfService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/materias/{materiaId}/pdfs")
public class PdfController {

    private final PdfService pdfService;
    private final UsuarioAtual usuarioAtual;

    public PdfController(PdfService pdfService, UsuarioAtual usuarioAtual) {
        this.pdfService = pdfService;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping(consumes = "multipart/form-data")
    public PdfResumo upload(Authentication auth, @PathVariable Long materiaId, @RequestParam("arquivo") MultipartFile arquivo) {
        Usuario usuario = usuarioAtual.resolver(auth);
        Pdf pdf = pdfService.upload(usuario, materiaId, arquivo);
        return new PdfResumo(pdf.getId(), pdf.getNomeArquivo());
    }

    @GetMapping
    public java.util.List<com.studiy.backend.dto.PdfResponse> listar(Authentication auth, @PathVariable Long materiaId) {
        return pdfService.listar(usuarioAtual.resolver(auth), materiaId);
    }

    /** O PDF já convertido em Markdown. O front transforma em arquivo .md para baixar. */
    @GetMapping("/{pdfId}/markdown")
    public PdfService.Markdown markdown(Authentication auth, @PathVariable Long materiaId, @PathVariable Long pdfId) {
        return pdfService.markdown(usuarioAtual.resolver(auth), materiaId, pdfId);
    }

    public record PdfResumo(Long id, String nomeArquivo) {}
}
