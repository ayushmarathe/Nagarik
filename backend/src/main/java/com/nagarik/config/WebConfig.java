package com.nagarik.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * The Vite dev server proxies /api to this app, so in normal development no
 * cross-origin request is ever made. This exists so the API still works if the
 * frontend is served from somewhere else (a static host, another port).
 *
 * Exposed as a CorsConfigurationSource bean rather than through
 * WebMvcConfigurer.addCorsMappings, because Spring Security's filter chain runs
 * before Spring MVC and reads a source bean directly. Configuring both would
 * give two answers to the same question, and the security filter's answer is
 * the one that takes effect.
 *
 * The method name is part of the contract. Spring Security finds this by the
 * bean name "corsConfigurationSource", not by type - the type is ambiguous,
 * since Spring MVC's mvcHandlerMappingIntrospector implements the same
 * interface. Rename this method and CORS quietly stops being applied.
 */
@Configuration
public class WebConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // Not needed while the token is read from the body of the response, but
        // harmless and saves a confusing afternoon if a header is ever read.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        // Anything Spring cannot route ends up forwarded to /error. Without a
        // mapping here the browser reports a CORS failure instead of the real
        // 404 or 500, which is a needlessly confusing way to find a typo in a URL.
        source.registerCorsConfiguration("/error", config);
        return source;
    }
}
