package com.nagarik.web.dto;

/**
 * What a successful sign-up or sign-in returns: the token, how long it is good
 * for, and who it belongs to.
 *
 * The user is included alongside the token so the interface can paint the
 * signed-in header from one response instead of immediately making a second
 * call to find out who it just signed in as.
 */
public record AuthResponse(
        String token,
        long expiresInHours,
        UserResponse user
) {
}
