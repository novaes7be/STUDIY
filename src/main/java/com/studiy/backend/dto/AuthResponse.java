package com.studiy.backend.dto;

import com.studiy.backend.model.Plano;

public record AuthResponse(String token, String nome, String email, Plano plano) {}
