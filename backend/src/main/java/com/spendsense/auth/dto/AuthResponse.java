package com.spendsense.auth.dto;

import com.spendsense.user.UserResponse;

public record AuthResponse(String accessToken, String tokenType, UserResponse user) {

    public static AuthResponse bearer(String token, UserResponse user) {
        return new AuthResponse(token, "Bearer", user);
    }

    public static AuthResponse pendingVerification(UserResponse user) {
        return new AuthResponse(null, null, user);
    }
}
