package com.nagarik.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One reported problem.
 *
 * voteCount and commentCount are denormalized onto this row so the feed can be
 * sorted by them without a join or a group-by. IssueService.toggleVote and
 * CommentService.add are the only writers - keep it that way or the counters
 * will drift. confirmedCount and deniedCount follow the same rule and belong to
 * IssueService.confirmFix alone.
 *
 * The verify* fields are a snapshot taken at the moment a moderator says the
 * work is done. They are deliberately frozen rather than recomputed: the quorum
 * is measured against the backing the report had when the claim was made, so
 * people arriving afterwards cannot raise or lower the bar the claim has to
 * clear.
 */
@Entity
@Table(
        name = "issue",
        indexes = {
                // The feed is always sorted by one of these three and usually
                // filtered by one of the next two, so each gets an index. They
                // earn their keep the moment the table outgrows a single page.
                @Index(name = "idx_issue_vote_count", columnList = "vote_count"),
                @Index(name = "idx_issue_comment_count", columnList = "comment_count"),
                @Index(name = "idx_issue_created_at", columnList = "created_at"),
                @Index(name = "idx_issue_category", columnList = "category"),
                @Index(name = "idx_issue_status", columnList = "status"),
                // The dashboard's two standing questions: what is late, and what
                // verification window is about to close.
                @Index(name = "idx_issue_eta_date", columnList = "eta_date"),
                @Index(name = "idx_issue_verify_deadline", columnList = "verify_deadline")
        }
)
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 140)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private IssueStatus status = IssueStatus.REPORTED;

    /** Free-text locality, ward or landmark. Kept as text so it works in any city. */
    @Column(nullable = false, length = 120)
    private String area;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "vote_count", nullable = false)
    private int voteCount = 0;

    @Column(name = "comment_count", nullable = false)
    private int commentCount = 0;

    // ---- What the council has promised ------------------------------------

    /**
     * The date work is expected to be finished by. A date rather than a timestamp
     * because that is the resolution anyone can actually commit to, and because
     * "by the 14th" is what a resident wants to read.
     */
    @Column(name = "eta_date")
    private LocalDate etaDate;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @ManyToOne
    @JoinColumn(name = "acknowledged_by_id")
    private AppUser acknowledgedBy;

    /**
     * The moderator's public note - "contractor scheduled", "waiting on the
     * water board". Shown to residents, so it is written for them.
     */
    @Column(name = "work_note", length = 500)
    private String workNote;

    // ---- The verification window -----------------------------------------

    @Column(name = "verify_opened_at")
    private Instant verifyOpenedAt;

    /** After this moment the window settles on whatever the votes say. */
    @Column(name = "verify_deadline")
    private Instant verifyDeadline;

    /**
     * How many votes settle this early, snapshotted when the window opened.
     *
     * The int columns below carry an explicit database default. Postgres refuses
     * "add column ... not null" on a table that already holds rows unless one is
     * supplied, and ddl-auto=update does exactly that when these fields land on
     * an existing issue table.
     */
    @Column(name = "verify_quorum", nullable = false, columnDefinition = "integer not null default 0")
    private int verifyQuorum = 0;

    @Column(name = "confirmed_count", nullable = false, columnDefinition = "integer not null default 0")
    private int confirmedCount = 0;

    @Column(name = "denied_count", nullable = false, columnDefinition = "integer not null default 0")
    private int deniedCount = 0;

    /** When the fix was confirmed. Null until a verification window agrees. */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Issue() {
        // required by JPA
    }

    public Issue(String title, String description, Category category, String area, AppUser author) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.area = area;
        this.author = author;
        this.status = IssueStatus.REPORTED;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public IssueStatus getStatus() {
        return status;
    }

    public void setStatus(IssueStatus status) {
        this.status = status;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public AppUser getAuthor() {
        return author;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Only the seeder uses this, to spread sample reports back across a few
     * weeks so "newest" has something meaningful to sort. Real reports are
     * stamped once, in the constructor.
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public int getVoteCount() {
        return voteCount;
    }

    public void setVoteCount(int voteCount) {
        this.voteCount = voteCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(int commentCount) {
        this.commentCount = commentCount;
    }

    public LocalDate getEtaDate() {
        return etaDate;
    }

    public void setEtaDate(LocalDate etaDate) {
        this.etaDate = etaDate;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public AppUser getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public void setAcknowledgedBy(AppUser acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }

    public String getWorkNote() {
        return workNote;
    }

    public void setWorkNote(String workNote) {
        this.workNote = workNote;
    }

    public Instant getVerifyOpenedAt() {
        return verifyOpenedAt;
    }

    public void setVerifyOpenedAt(Instant verifyOpenedAt) {
        this.verifyOpenedAt = verifyOpenedAt;
    }

    public Instant getVerifyDeadline() {
        return verifyDeadline;
    }

    public void setVerifyDeadline(Instant verifyDeadline) {
        this.verifyDeadline = verifyDeadline;
    }

    public int getVerifyQuorum() {
        return verifyQuorum;
    }

    public void setVerifyQuorum(int verifyQuorum) {
        this.verifyQuorum = verifyQuorum;
    }

    public int getConfirmedCount() {
        return confirmedCount;
    }

    public void setConfirmedCount(int confirmedCount) {
        this.confirmedCount = confirmedCount;
    }

    public int getDeniedCount() {
        return deniedCount;
    }

    public void setDeniedCount(int deniedCount) {
        this.deniedCount = deniedCount;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    /**
     * Past its promised date with the work still outstanding. Verification and
     * the two settled states are excluded: once somebody has claimed the work is
     * finished, a date in the past is a record rather than a debt.
     */
    public boolean isOverdue(LocalDate today) {
        return etaDate != null
                && status.acceptsEta()
                && etaDate.isBefore(today);
    }

    /** Total verification votes cast so far, either way. */
    public int getVerifyVotesCast() {
        return confirmedCount + deniedCount;
    }

    /**
     * Wipes the verification snapshot. Used when a disputed report goes back for
     * more work, so the second window starts from zero rather than inheriting
     * the tally that rejected the first attempt.
     */
    public void clearVerification() {
        this.verifyOpenedAt = null;
        this.verifyDeadline = null;
        this.verifyQuorum = 0;
        this.confirmedCount = 0;
        this.deniedCount = 0;
    }
}
