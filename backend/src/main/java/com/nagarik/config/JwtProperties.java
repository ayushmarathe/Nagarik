package com.nagarik.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings for token issuing, from application.properties:
 *
 *   nagarik.jwt.secret=...
 *   nagarik.jwt.expiry-hours=168
 *
 * A mutable class rather than a record, because this binds by setter and that
 * is the binding style that cannot surprise anyone at startup.
 */
@Component
@ConfigurationProperties(prefix = "nagarik.jwt")
public class JwtProperties {

    private String secret = "";

    /** Seven days. There is no refresh-token flow, so this is how long a
     *  person stays signed in; treat it as a product decision, not a detail. */
    private long expiryHours = 168;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpiryHours() {
        return expiryHours;
    }

    public void setExpiryHours(long expiryHours) {
        this.expiryHours = expiryHours;
    }
}
