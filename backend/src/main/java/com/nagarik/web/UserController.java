package com.nagarik.web;

import com.nagarik.domain.AppUser;
import com.nagarik.service.UserService;
import com.nagarik.web.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.nagarik.security.AuthPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signed-in-user routes all moved to /api/auth (register, login, me, rename),
 * because they are about a session rather than about a user record.
 *
 * What used to live here, and why it is gone: POST /api/users claimed a name
 * and handed back a user id that the client then sent as an X-User-Id header.
 * That made identity something the caller asserted, so anyone could vote or
 * post as anyone else by editing a header. Identity is now a signed token that
 * only JwtAuthFilter can produce.
 *
 * This class is kept only because it could not be removed. It has no routes and
 * can be deleted.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Unused, and deliberately so - see the class comment. */
    @SuppressWarnings("unused")
    private AppUser self(AuthPrincipal principal) {
        return userService.require(principal);
    }

    /** Placeholder so the import above stays meaningful; never mapped. */
    @SuppressWarnings("unused")
    private UserResponse neverMapped() {
        return null;
    }
}
