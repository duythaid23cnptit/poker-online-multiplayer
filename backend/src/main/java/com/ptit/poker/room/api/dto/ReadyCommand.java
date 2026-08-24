package com.ptit.poker.room.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReadyCommand(@NotNull UUID clientCommandId, boolean ready) {
}
