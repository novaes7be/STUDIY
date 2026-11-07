package com.studiy.backend.service;

import com.studiy.backend.dto.MateriaRequest;
import com.studiy.backend.dto.MateriaResponse;
import com.studiy.backend.model.Materia;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.FlashcardRepository;
import com.studiy.backend.repository.MateriaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class MateriaService {

    private final MateriaRepository materiaRepository;
    private final FlashcardRepository flashcardRepository;

    public MateriaService(MateriaRepository materiaRepository, FlashcardRepository flashcardRepository) {
        this.materiaRepository = materiaRepository;
        this.flashcardRepository = flashcardRepository;
    }

    public List<MateriaResponse> listar(Usuario usuario) {
        return materiaRepository.findByUsuarioId(usuario.getId())
                .stream()
                .map(this::paraResponse)
                .toList();
    }

    public MateriaResponse criar(Usuario usuario, MateriaRequest request) {
        Materia materia = Materia.builder()
                .nome(request.nome())
                .cor(request.cor() != null ? request.cor() : "verde")
                .usuario(usuario)
                .build();

        return paraResponse(materiaRepository.save(materia));
    }

    public MateriaResponse atualizar(Usuario usuario, Long id, MateriaRequest request) {
        Materia materia = buscarDoUsuario(usuario, id);
        materia.setNome(request.nome());
        if (request.cor() != null) {
            materia.setCor(request.cor());
        }
        return paraResponse(materiaRepository.save(materia));
    }

    public void deletar(Usuario usuario, Long id) {
        Materia materia = buscarDoUsuario(usuario, id);
        materiaRepository.delete(materia);
    }

    public Materia buscarDoUsuario(Usuario usuario, Long id) {
        return materiaRepository.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Matéria não encontrada"));
    }

    private MateriaResponse paraResponse(Materia m) {
        long pendentes = flashcardRepository.countByMateriaIdAndProximaRevisaoLessThanEqual(m.getId(), LocalDate.now());
        return new MateriaResponse(m.getId(), m.getNome(), m.getCor(), m.getProgressoAtual(), m.getProgressoTotal(), pendentes);
    }
}
