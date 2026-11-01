package com.studiy.backend.service;

import com.studiy.backend.dto.AtividadeRequest;
import com.studiy.backend.dto.AtividadeResponse;
import com.studiy.backend.model.Atividade;
import com.studiy.backend.model.Materia;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.AtividadeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AtividadeService {

    private final AtividadeRepository atividadeRepository;
    private final MateriaService materiaService;
    private final GamificacaoService gamificacaoService;

    public AtividadeService(AtividadeRepository atividadeRepository, MateriaService materiaService, GamificacaoService gamificacaoService) {
        this.atividadeRepository = atividadeRepository;
        this.materiaService = materiaService;
        this.gamificacaoService = gamificacaoService;
    }

    public List<AtividadeResponse> listar(Usuario usuario, Long materiaId) {
        materiaService.buscarDoUsuario(usuario, materiaId); // garante posse
        return atividadeRepository.findByMateriaIdOrderByDataAtividadeAsc(materiaId)
                .stream().map(this::paraResponse).toList();
    }

    public AtividadeResponse criar(Usuario usuario, Long materiaId, AtividadeRequest request) {
        Materia materia = materiaService.buscarDoUsuario(usuario, materiaId);

        Atividade atividade = Atividade.builder()
                .titulo(request.titulo())
                .dataAtividade(request.dataAtividade())
                .tag(request.tag())
                .tagTipo(request.tagTipo() != null ? request.tagTipo() : "info")
                .materia(materia)
                .concluida(false)
                .build();

        return paraResponse(atividadeRepository.save(atividade));
    }

    public AtividadeResponse concluir(Usuario usuario, Long materiaId, Long atividadeId) {
        materiaService.buscarDoUsuario(usuario, materiaId);
        Atividade atividade = atividadeRepository.findByIdAndMateriaId(atividadeId, materiaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Atividade não encontrada"));

        boolean novoEstado = !Boolean.TRUE.equals(atividade.getConcluida());
        atividade.setConcluida(novoEstado);
        Atividade salva = atividadeRepository.save(atividade);

        if (novoEstado) {
            gamificacaoService.registrarAtividade(usuario);
        }

        return paraResponse(salva);
    }

    public void deletar(Usuario usuario, Long materiaId, Long atividadeId) {
        materiaService.buscarDoUsuario(usuario, materiaId);
        Atividade atividade = atividadeRepository.findByIdAndMateriaId(atividadeId, materiaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Atividade não encontrada"));
        atividadeRepository.delete(atividade);
    }

    private AtividadeResponse paraResponse(Atividade a) {
        return new AtividadeResponse(a.getId(), a.getTitulo(), a.getDataAtividade(), a.getTag(), a.getTagTipo(), Boolean.TRUE.equals(a.getConcluida()));
    }
}
