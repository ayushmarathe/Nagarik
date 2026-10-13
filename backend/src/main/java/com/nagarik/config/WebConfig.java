package com.nagarik.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Cross-origin rules for the API.
 *
 * In normal development no cross-origin request happens at all: Vite proxies
 * /api through to Spring Boot, so the browser sees one origin. This exists for
 * the case where the frontend is served from somewhere else - another port, or
 * a static host in front of this API.
 *
 * The method name is part of the contract. Spring Security's
 * .cors(Customizer.withDefaults()) looks for a bean literally named
 * "corsFilter" first, then one named "corsConfigurationSource". Rename this
 * method and it finds neither, falls back to Spring MVC's introspector, and
 * CORS quietly stops being applied - with no error at startup.
 */
@Configuration
public class WebConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Where the frontend may be served from. Origins are scheme + host +
        // port and nothing else - a trailing slash makes an entry that can
        // never match, which is a confusing afternoon because the request looks
        // correct and is still refused.
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173"
        ));

        config.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        config.setAllowedHeaders(List.of("*"));

        // Identity travels in an Authorization header, not a cookie, so the
        // browser never needs to attach credentials to a cross-origin call.
        // Leaving this on would widen what a hostile page can do with a session
        // it has no other way to reach, in exchange for nothing.
        config.setAllowCredentials(false);

        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        // Anything Spring cannot route is forwarded to /error, and without a
        // mapping here the browser reports that as a CORS failure instead of the
        // real 404 - which sends you looking for a CORS bug that is not there.
        source.registerCorsConfiguration("/error", config);

        return new CorsFilter(source);
    }
}
