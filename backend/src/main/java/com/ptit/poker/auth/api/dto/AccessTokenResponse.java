package com.ptit.poker.auth.api.dto;

public record AccessTokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}

