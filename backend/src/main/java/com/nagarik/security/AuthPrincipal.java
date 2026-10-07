package com.nagarik.security;

import com.nagarik.domain.Role;

/**
 * Who is making this request, as read back out of a verified token.
 *
 * Deliberately not the AppUser entity: this is stored in the security context
 * and lives for the length of one request, and a detached JPA entity in there
 * invites lazy-loading surprises for no benefit. The controllers still ask
 * UserService for the real row when they need to attach an author to something.
 */
public record AuthPrincipal(
        Long id,
        String email,
        String displayName,
        Role role
) {
    /** Spring Security wants authorities as strings, and "hasRole" expects the
     *  ROLE_ prefix, so the name of the enum gets one here rather than at a
     *  dozen call sites. */
    public String authority() {
        return "ROLE_" + role.name();
    }
}
