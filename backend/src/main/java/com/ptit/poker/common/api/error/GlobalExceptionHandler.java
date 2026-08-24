package com.ptit.poker.common.api.error;

import com.ptit.poker.auth.application.exception.AccountLockedException;
import com.ptit.poker.auth.application.exception.AuthenticationFailedException;
import com.ptit.poker.auth.application.exception.DuplicateAccountException;
import com.ptit.poker.auth.application.exception.InvalidRefreshTokenException;
import com.ptit.poker.player.application.exception.ProfileNotFoundException;
import com.ptit.poker.room.application.exception.RoomBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RoomBusinessException.class)
    ResponseEntity<ApiError> roomBusiness(RoomBusinessException exception) {
        return ResponseEntity.status(exception.status()).body(ApiError.of(exception.code(), exception.getMessage()));
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldValidationError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldValidationError(
                        error.getField(), error.getCode(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(
                new ApiError("VALIDATION_FAILED", "Request validation failed", fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableRequest() {
        return ResponseEntity.badRequest().body(
                ApiError.of("INVALID_REQUEST", "Request body is malformed or unreadable"));
    }

    @ExceptionHandler(DuplicateAccountException.class)
    ResponseEntity<ApiError> handleDuplicate(DuplicateAccountException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiError.of("DUPLICATE_ACCOUNT", exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrityConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiError.of("RESOURCE_CONFLICT", "The requested value conflicts with existing data"));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ApiError> handleAuthenticationFailure() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiError.of("AUTHENTICATION_FAILED", "Invalid credentials"));
    }

    @ExceptionHandler(AccountLockedException.class)
    ResponseEntity<ApiError> handleLockedAccount() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiError.of("ACCOUNT_LOCKED", "Account is locked"));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<ApiError> handleInvalidRefreshToken() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiError.of("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired"));
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    ResponseEntity<ApiError> handleMissingProfile() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiError.of("PROFILE_NOT_FOUND", "Player profile was not found"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        LOGGER.error("Unexpected request failure: {}", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiError.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
