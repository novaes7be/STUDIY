package com.studiy.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(@Email @NotBlank @jakarta.validation.constraints.Size(max = 254) String email) {}
