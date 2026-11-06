package com.studiy.backend.dto;

import com.studiy.backend.model.RespostaRevisao;
import jakarta.validation.constraints.NotNull;

/** O aluno diz como foi; a qualidade numérica do SM-2 é decidida no servidor. */
public record RevisarFlashcardRequest(@NotNull RespostaRevisao resposta) {}
