package com.nagarik.config;

import com.nagarik.security.JwtAuthFilter;
import com.nagarik.security.RestSecurityHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Who can reach what.
 *
 * The board is readable by anyone, signed in or not - a civic board nobody can
 * read without an account is a worse civic board. Everything that writes
 * (posting, backing, replying) needs an account, and the one action that
 * changes what the board claims is true about the world - moving a report to
 * Acknowledged or Resolved - needs a moderator.
 *
 * Rules are matched top to bottom and the first match wins, so anything
 * broader than a rule must come after it.
 *
 * Spring Security 6 configures through lambdas; the older
 * ".and()"-chained style is removed in 6.x.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthFilter jwtFilter,
                                           RestSecurityHandlers handlers) throws Exception {

        http
                // A browser-only API holding a bearer token has no cookie to
                // forge a cross-site request with, so CSRF tokens buy nothing
                // here and would only break the JSON clients.
                .csrf(AbstractHttpConfigurer::disable)
                // Deliberately not injecting a CorsConfigurationSource here.
                // Spring MVC's own mvcHandlerMappingIntrospector also implements
                // that interface, so asking for the type by injection finds two
                // candidates and the context fails to start. withDefaults() makes
                // Spring Security look the source up by the bean name
                // "corsConfigurationSource" instead, which is unambiguous.
                //
                // That means WebConfig's method name is load-bearing: rename it
                // and CORS silently stops being applied.
                .cors(Customizer.withDefaults())
                // No server-side session: the token is the session. This is
                // also what keeps the API honest behind a load balancer, since
                // no request depends on having hit the same instance before.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .authorizeHttpRequests(auth -> auth
                        // CORS preflight carries no Authorization header, so it
                        // has to be allowed before any authenticated rule or
                        // every cross-origin write fails with a 401 the browser
                        // reports as a CORS error.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Signing up and signing in are the two things you do
                        // without a token.
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()

                        // Reading the board, and the lookup lists the forms
                        // need to render.
                        .requestMatchers(HttpMethod.GET, "/api/issues", "/api/issues/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories", "/api/statuses").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/issues/*/comments").permitAll()

                        // Everything that writes needs an account.
                        .requestMatchers(HttpMethod.POST, "/api/issues").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/issues/*/vote").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/issues/*/comments").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/issues/*/confirm").authenticated()
                        .requestMatchers("/api/auth/me").authenticated()

                        // The dashboard, and every action that changes what the
                        // board claims is true about the world. One rule for the
                        // whole prefix rather than a list of verbs, so a
                        // transition added next month is covered the moment it
                        // is mapped under /api/admin. Also enforced in
                        // IssueService, which is the copy that survives somebody
                        // mounting an admin action somewhere else.
                        .requestMatchers("/api/admin/**").hasAnyRole("MODERATOR", "ADMIN")

                        // Spring forwards unhandled errors to /error, and that
                        // forward goes through this chain again. Without this
                        // line it meets anyRequest().authenticated() below, and
                        // a plain 404 on a mistyped URL comes back as a 401
                        // telling the caller to sign in. It carries no data of
                        // its own - the body is whatever the original failure
                        // produced.
                        .requestMatchers("/error").permitAll()

                        // Anything added later. Default-deny is the safe
                        // direction to fail in: a new endpoint is locked until
                        // it is listed above.
                        .anyRequest().authenticated());

        // Runs before the username/password filter so the security context is
        // already populated by the time anything reads it.
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt at the default strength of 10. The cost is per-hash and deliberate:
     * it is what makes a stolen table of hashes expensive to attack. Raising it
     * slows every login down; lowering it to speed up a login is how a password
     * store becomes cheap to crack.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
