package com.nagarik.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * One person's answer to "was this actually fixed?".
 *
 * Separate from Vote on purpose. Backing a report says the problem is real;
 * this says the repair happened. They are different claims, made at different
 * times, and a person can be right about one and wrong about the other - so
 * collapsing them into one table would lose the distinction that makes
 * verification worth anything.
 *
 * The unique constraint is what stops one enthusiastic resident from deciding
 * the outcome alone.
 */
@Entity
@Table(
        name = "confirmation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_confirmation_issue_user",
                columnNames = {"issue_id", "user_id"}),
        // The unique constraint covers lookups that start with issue_id. This
        // covers "which of these reports have I already answered", asked once
        // per feed load for a signed-in viewer.
        indexes = @Index(name = "idx_confirmation_user", columnList = "user_id")
)
public class Confirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issue;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** True for "yes, it is fixed", false for "no, it is not". */
    @Column(name = "fixed", nullable = false)
    private boolean fixed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Set when somebody changes their answer while the window is still open. */
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Confirmation() {
        // required by JPA
    }

    public Confirmation(Issue issue, AppUser user, boolean fixed) {
        this.issue = issue;
        this.user = user;
        this.fixed = fixed;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Issue getIssue() {
        return issue;
    }

    public AppUser getUser() {
        return user;
    }

    public boolean isFixed() {
        return fixed;
    }

    /**
     * People are allowed to change their mind while the window is open - someone
     * who says "not fixed" on Monday and finds the pothole filled on Tuesday
     * should be able to say so, and forcing them to live with the first answer
     * would make the tally less accurate rather than more.
     */
    public void setFixed(boolean fixed) {
        this.fixed = fixed;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
