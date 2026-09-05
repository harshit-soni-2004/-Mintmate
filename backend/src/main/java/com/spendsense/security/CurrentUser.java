package com.spendsense.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public SpendSenseUserPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SpendSenseUserPrincipal principal)) {
            throw new IllegalStateException("Authenticated user is required");
        }
        return principal;
    }
}
