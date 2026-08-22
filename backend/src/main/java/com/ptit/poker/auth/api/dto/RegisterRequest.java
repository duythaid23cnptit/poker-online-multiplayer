package com.ptit.poker.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "must contain only letters, numbers, and underscores")
        String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 2, max = 100) String displayName) {
}

