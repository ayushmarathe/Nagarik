package com.nagarik.service;

import com.nagarik.domain.AppUser;
import com.nagarik.domain.Role;
import com.nagarik.error.ConflictException;
import com.nagarik.error.UnauthenticatedException;
import com.nagarik.repo.AppUserRepository;
import com.nagarik.security.AuthPrincipal;
import com.nagarik.security.JwtService;
import com.nagarik.web.dto.AuthResponse;
import com.nagarik.web.dto.LoginRequest;
import com.nagarik.web.dto.RegisterRequest;
import com.nagarik.web.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accounts.
 *
 * The rules that matter, all in one place:
 *
 *  - Passwords are hashed with BCrypt and never stored, logged or returned.
 *  - A failed sign-in says the same thing whether the address is unknown or the
 *    password is wrong. Distinguishing them turns the login form into a way to
 *    ask "does this person have an account here?", which for a civic app that
 *    shows your name and street is information worth not giving away.
 *  - The unknown-address path still pays for a BCrypt check against a dummy
 *    hash, so responses take the same time either way. Without that, the
 *    difference in timing answers the question the message refuses to.
 */
@Service
public class UserService {

    /**
     * A real BCrypt hash of a value nothing can match, used only to burn the
     * same CPU time as a genuine check when the address is unknown.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService tokens;

    public UserService(AppUserRepository users, PasswordEncoder encoder, JwtService tokens) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Checked here as well as by the unique constraints, because a named
        // field is a far better message than "that clashed with something
        // already saved". The constraints remain the real guarantee under
        // concurrency; this is the good-mannered path.
        if (users.existsByEmail(request.email())) {
            throw new ConflictException("There is already an account with that email - sign in instead");
        }
        if (users.existsByDisplayNameIgnoreCase(request.displayName())) {
            throw new ConflictException("That name is already on the board - try another");
        }

        AppUser user = users.save(new AppUser(
                request.displayName(),
                request.email(),
                encoder.encode(request.password()),
                Role.RESIDENT));

        return signedIn(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = users.findByEmail(request.email()).orElse(null);

        if (user == null || user.getPasswordHash() == null) {
            // Spend the time anyway, then fail the same way an existing account
            // with the wrong password would.
            encoder.matches(request.password(), DUMMY_HASH);
            throw new UnauthenticatedException("Email or password is not right");
        }

        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthenticatedException("Email or password is not right");
        }

        return signedIn(user);
    }

    /** The signed-in account, refreshed from the database. */
    @Transactional(readOnly = true)
    public AppUser require(AuthPrincipal principal) {
        if (principal == null) {
            throw new UnauthenticatedException("Sign in to do that");
        }
        return users.findById(principal.id())
                .orElseThrow(() -> new UnauthenticatedException(
                        "That account no longer exists - sign in again"));
    }

    @Transactional
    public UserResponse rename(AuthPrincipal principal, String displayName) {
        AppUser user = require(principal);

        users.findByDisplayNameIgnoreCase(displayName)
                .filter(other -> !other.getId().equals(user.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("That name is already on the board - try another");
                });

        user.setDisplayName(displayName);
        return UserResponse.of(users.save(user));
    }

    private AuthResponse signedIn(AppUser user) {
        return new AuthResponse(
                tokens.issue(user),
                tokens.getExpiryHours(),
                UserResponse.of(user));
    }
}
