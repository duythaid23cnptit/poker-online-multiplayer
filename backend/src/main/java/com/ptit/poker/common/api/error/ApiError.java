package com.ptit.poker.common.api.error;

import java.util.List;

public record ApiError(String code, String message, List<FieldValidationError> fieldErrors) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of());
    }
}

