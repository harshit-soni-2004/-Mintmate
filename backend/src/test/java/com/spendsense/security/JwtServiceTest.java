package com.spendsense.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendsense.user.User;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void issueAndParseRoundTrip() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("local-dev-only-change-me-spendsense-jwt-hs256-key-32b");
        properties.setExpirationMs(60_000L);
        JwtService jwtService = new JwtService(properties);

        User user = new User("ada@example.com", "hash", "Ada", "Asia/Kolkata");
        String token = jwtService.issueToken(user);

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.parseUserId(token)).isEqualTo(user.getId());
        assertThat(jwtService.isValid("not-a-token")).isFalse();
    }
}
