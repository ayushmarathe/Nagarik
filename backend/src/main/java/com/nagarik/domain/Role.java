package com.nagarik.domain;

/**
 * What an account is allowed to do.
 *
 * RESIDENT is everyone who signs up. MODERATOR can move a report's status -
 * the one action that changes what the board says is true about the world, so
 * it is the one action worth restricting. ADMIN exists so that granting
 * moderator rights is itself something only an admin can do, rather than
 * something anybody can hand themselves.
 */
public enum Role {

    RESIDENT("Resident"),
    MODERATOR("Moderator"),
    ADMIN("Admin");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Can this role put a report into a different state? Used by the service as
     * well as the controller, so a new route that forgets an annotation still
     * cannot let a resident close a report.
     */
    public boolean canModerate() {
        return this == MODERATOR || this == ADMIN;
    }
}
