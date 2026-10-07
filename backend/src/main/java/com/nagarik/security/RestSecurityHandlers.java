package com.nagarik.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The two ways a request can be refused, answered in the same JSON shape the
 * rest of the API uses.
 *
 * Without this, Spring Security replies with an empty body (or a login page
 * redirect), and the frontend's error handling - which reads a "message" field -
 * falls back to "Something went wrong. Try again." for what is really "you are
 * signed out" or "you are not a moderator". Both of those are worth saying
 * plainly.
 */
@Component
public class RestSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper json;

    public RestSecurityHandlers(ObjectMapper json) {
        this.json = json;
    }

    /** No token, or one that no longer verifies. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED,
                "Sign in to do that");
    }

    /** Signed in, but not allowed - a resident trying to close a report. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN,
                "Only a moderator can change a report's status");
    }

    private void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        json.writeValue(response.getWriter(), body);
    }
}
