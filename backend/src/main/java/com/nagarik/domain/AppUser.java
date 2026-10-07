package com.nagarik.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Named "app_user" because "user" is a reserved word in Postgres - bare USER is
 * a function returning the current session user.
 *
 * Accounts used to be a name and nothing else. They now carry an email and a
 * BCrypt hash, and the name is just what the board prints next to a report.
 *
 * The three columns added for that are deliberately NULLABLE at the database
 * level, including role, which carries a DEFAULT instead of NOT NULL. This app
 * runs with ddl-auto=update, and Hibernate adds new columns with a plain
 * ALTER TABLE ADD COLUMN - which Postgres refuses if the column is NOT NULL and
 * the table already has rows. Anyone keeping a database from the name-only
 * version would otherwise get a startup failure instead of an upgrade. Once
 * this is on Flyway, tighten role to NOT NULL and drop the default.
 */
@Entity
@Table(
        name = "app_user",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_app_user_display_name", columnNames = "display_name"),
                @UniqueConstraint(name = "uk_app_user_email", columnNames = "email")
        }
)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    /** Login identity. Lower-cased before it is stored, so lookups are exact. */
    @Column(name = "email", length = 254)
    private String email;

    /**
     * A BCrypt hash, never the password. BCrypt embeds its own salt and cost in
     * the string, so it is a fixed 60 characters - 72 would be plenty, but the
     * column is generous because the algorithm can be swapped later.
     */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, columnDefinition = "varchar(20) default 'RESIDENT'")
    private Role role = Role.RESIDENT;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AppUser() {
        // required by JPA
    }

    public AppUser(String displayName, String email, String passwordHash, Role role) {
        this.displayName = displayName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role == null ? Role.RESIDENT : role;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Rows created before accounts had roles have a null here, and so does
     * anything inserted by hand. Neither is a moderator.
     */
    public Role getRole() {
        return role == null ? Role.RESIDENT : role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
