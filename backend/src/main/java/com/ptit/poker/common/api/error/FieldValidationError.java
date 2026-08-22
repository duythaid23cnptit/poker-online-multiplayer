package com.ptit.poker.common.api.error;

public record FieldValidationError(String field, String code, String message) {
}

