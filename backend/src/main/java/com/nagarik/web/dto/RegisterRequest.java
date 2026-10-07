package com.nagarik.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Signing up.
 *
 * The password is capped at 72 characters because that is where BCrypt stops
 * reading - anything past the 72nd byte is ignored by the algorithm. Rejecting
 * a longer password is honest about that; silently accepting it would mean a
 * hundred-character password offering the security of its first 72 bytes.
 */
public record RegisterRequest(

        @NotBlank(message = "Enter the name your neighbours will see")
        @Size(min = 2, max = 60, message = "Keep the name between 2 and 60 characters")
        String displayName,

        @NotBlank(message = "Enter your email")
        @Email(message = "That does not look like an email address")
        @Size(max = 254, message = "That email address is too long")
        String email,

        @NotBlank(message = "Choose a password")
        @Size(min = 8, max = 72, message = "Use at least 8 characters")
        String password
) {
    public RegisterRequest {
        displayName = displayName == null ? null : displayName.trim();
        email = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
