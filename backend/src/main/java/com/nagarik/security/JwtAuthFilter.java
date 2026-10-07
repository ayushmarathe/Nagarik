package com.nagarik.security;

import com.nagarik.domain.AppUser;
import com.nagarik.repo.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads "Authorization: Bearer <token>" and, if it verifies, marks the request
 * as that person.
 *
 * The account is read back from the database on every request rather than
 * trusted from the token's claims. That costs one lookup by primary key on a
 * path that already runs several queries, and it buys two things worth having:
 * a deleted account stops working immediately instead of at token expiry, and
 * a role change (somebody made a moderator) takes effect on their next click
 * rather than a week later.
 *
 * A bad token is not an error here. It leaves the request anonymous and lets
 * the route rules decide, so public reads keep working for a browser holding a
 * token that quietly expired.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService tokens;
    private final AppUserRepository users;

    public JwtAuthFilter(JwtService tokens, AppUserRepository users) {
        this.tokens = tokens;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        readToken(request)
                .flatMap(tokens::userId)
                .flatMap(users::findById)
                .ifPresent(this::authenticate);

        chain.doFilter(request, response);
    }

    private Optional<String> readToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private void authenticate(AppUser user) {
        AuthPrincipal principal = new AuthPrincipal(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());

        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(principal.authority())));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
