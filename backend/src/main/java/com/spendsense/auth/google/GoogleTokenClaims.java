package com.spendsense.auth.google;

public record GoogleTokenClaims(
        String sub,
        String email,
        boolean emailVerified,
        String name
) {}
