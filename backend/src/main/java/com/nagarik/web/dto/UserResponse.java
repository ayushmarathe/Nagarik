package com.nagarik.web.dto;

import com.nagarik.domain.AppUser;

import java.time.Instant;

/**
 * A person, as the interface is allowed to see them. Never carries the hash.
 */
public record UserResponse(
        Long id,
        String displayName,
        String email,
        String role,
        String roleLabel,
        boolean canModerate,
        Instant createdAt
) {
    public static UserResponse of(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole().name(),
                user.getRole().getLabel(),
                user.getRole().canModerate(),
                user.getCreatedAt()
        );
    }
}
