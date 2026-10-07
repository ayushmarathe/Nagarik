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

import java.time.Instant;

/**
 * Named "issue_comment" because "comment" is a reserved word in Postgres - it
 * is the statement that attaches a description to a table or column.
 */
@Entity
@Table(
        name = "issue_comment",
        // Every read of this table is "the replies on one report, oldest first".
        indexes = @Index(name = "idx_comment_issue", columnList = "issue_id, created_at")
)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issue;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Comment() {
        // required by JPA
    }

    public Comment(Issue issue, AppUser author, String body) {
        this.issue = issue;
        this.author = author;
        this.body = body;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Issue getIssue() {
        return issue;
    }

    public AppUser getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Used by the seeder to space sample replies out after their report. */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
