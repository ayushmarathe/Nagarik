package com.nagarik.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Signing in. There is no @Email here on purpose: the only thing worth saying
 * to someone who mistypes their address on the way in is that the address and
 * password do not match, and refusing to even look up "bob@" tells an attacker
 * which addresses are real. The format was enforced at registration.
 */
public record LoginRequest(

        @NotBlank(message = "Enter your email")
        String email,

        @NotBlank(message = "Enter your password")
        String password
) {
    public LoginRequest {
        email = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
