package com.ptit.poker.player.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(min = 2, max = 100) String displayName,
        @Size(max = 2048) String avatarUrl) {
}

