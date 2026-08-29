package com.ptit.poker.admin.api;

import jakarta.validation.constraints.Size;

public record AdminMutationRequest(@Size(max = 500) String reason) {}
