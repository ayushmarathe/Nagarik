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

/**
 * A photo or a short video attached to a report.
 *
 * The bytes are NOT in this table. Only the metadata is; the file itself sits
 * under the media directory named by nagarik.media.root. Putting video in a
 * BYTEA column makes every database backup carry the video with it and turns a
 * 40MB upload into a 40MB row, which Postgres will do but should not be asked
 * to.
 *
 * storedName is the only name that ever touches the filesystem, and it is
 * generated here rather than taken from the upload. A filename supplied by a
 * caller is an instruction to write wherever they like - "../../application.properties"
 * is a valid filename as far as the client is concerned. originalName is kept
 * purely so the interface can show people what they picked.
 *
 * contentType is what the bytes were sniffed to be, never what the upload
 * claimed. See MediaStore.
 */
@Entity
@Table(
        name = "attachment",
        indexes = {
                // Every read is "the files on one report, oldest first".
                @Index(name = "idx_attachment_issue", columnList = "issue_id, created_at")
        }
)
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issue;

    /** Who uploaded it - which is not always the person who raised the report. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private AppUser uploader;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MediaKind kind;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    /** The generated name on disk. Unique so two uploads cannot collide. */
    @Column(name = "stored_name", nullable = false, unique = true, length = 80)
    private String storedName;

    /** What the uploader called it. Display only - never used as a path. */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    /**
     * Pixel dimensions, for images only; null for video, because reading them
     * would mean decoding the container and that needs a library this project
     * does not have. They are stored so the interface can reserve the right
     * space before the file loads, which is what stops a gallery jumping as it
     * fills in.
     */
    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Attachment() {
        // required by JPA
    }

    public Attachment(Issue issue,
                      AppUser uploader,
                      MediaKind kind,
                      String contentType,
                      long byteSize,
                      String storedName,
                      String originalName,
                      Integer width,
                      Integer height) {
        this.issue = issue;
        this.uploader = uploader;
        this.kind = kind;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.storedName = storedName;
        this.originalName = originalName;
        this.width = width;
        this.height = height;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Issue getIssue() {
        return issue;
    }

    public AppUser getUploader() {
        return uploader;
    }

    public MediaKind getKind() {
        return kind;
    }

    public String getContentType() {
        return contentType;
    }

    public long getByteSize() {
        return byteSize;
    }

    public String getStoredName() {
        return storedName;
    }

    public String getOriginalName() {
        return originalName;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
