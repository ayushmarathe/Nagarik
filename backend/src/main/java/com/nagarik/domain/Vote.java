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
 * One person agreeing that one problem is real. The unique constraint is what
 * makes an upvote mean something - without it the count is just a click tally.
 */
@Entity
@Table(
        name = "vote",
        uniqueConstraints = @UniqueConstraint(name = "uk_vote_issue_user", columnNames = {"issue_id", "user_id"}),
        // The unique constraint already indexes (issue_id, user_id), which covers
        // lookups starting with issue_id. This covers the other direction: "which
        // of these issues has this person backed", asked once per feed load.
        indexes = @Index(name = "idx_vote_user", columnList = "user_id")
)
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issue;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Vote() {
        // required by JPA
    }

    public Vote(Issue issue, AppUser user) {
        this.issue = issue;
        this.user = user;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
