package com.nagarik.service;

import com.nagarik.domain.AppUser;
import com.nagarik.domain.Attachment;
import com.nagarik.domain.Issue;
import com.nagarik.domain.MediaKind;
import com.nagarik.domain.Role;
import com.nagarik.error.BadRequestException;
import com.nagarik.error.ForbiddenException;
import com.nagarik.error.NotFoundException;
import com.nagarik.repo.AttachmentRepository;
import com.nagarik.repo.IssueRepository;
import com.nagarik.web.dto.AttachmentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Attaching evidence to a report, and taking it back off.
 *
 * Who may attach: any signed-in account. That is deliberate and worth being
 * able to defend - the person who first reported a pothole is often not the one
 * who later has a clear photo of it, and the whole point of the board is that a
 * problem belongs to the neighbourhood rather than to whoever typed it in
 * first. Deletion is narrower: the uploader or a moderator, because removing
 * someone else's evidence is a moderation act.
 */
@Service
public class AttachmentService {

    private final AttachmentRepository attachments;
    private final IssueRepository issues;
    private final MediaStore store;

    public AttachmentService(AttachmentRepository attachments,
                             IssueRepository issues,
                             MediaStore store) {
        this.attachments = attachments;
        this.issues = issues;
        this.store = store;
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(Long issueId, AppUser viewer) {
        if (!issues.existsById(issueId)) {
            throw new NotFoundException("There is no report with id " + issueId);
        }
        return attachments.findByIssueIdOrderByCreatedAtAsc(issueId).stream()
                .map(attachment -> toResponse(attachment, viewer))
                .toList();
    }

    @Transactional
    public AttachmentResponse add(Long issueId, AppUser uploader, MultipartFile file) {
        Issue issue = issues.findById(issueId)
                .orElseThrow(() -> new NotFoundException("There is no report with id " + issueId));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a file to attach.");
        }

        long already = attachments.countByIssueId(issueId);
        if (already >= MediaStore.MAX_PER_ISSUE) {
            throw new BadRequestException(
                    "A report can hold " + MediaStore.MAX_PER_ISSUE
                            + " files. Remove one before adding another.");
        }

        byte[] bytes = readAll(file);

        MediaStore.Sniffed sniffed = store.sniff(bytes);
        if (sniffed == null) {
            throw new BadRequestException(
                    "That file is not a photo or video this board can show. "
                            + "Use JPEG, PNG, GIF or WebP for photos, MP4 or WebM for video.");
        }

        long cap = store.capFor(sniffed.kind());
        if (bytes.length > cap) {
            throw new BadRequestException(
                    sniffed.kind() == MediaKind.VIDEO
                            ? "That video is over " + (cap / (1024 * 1024)) + "MB. Trim it and try again."
                            : "That photo is over " + (cap / (1024 * 1024)) + "MB.");
        }

        Integer width = null;
        Integer height = null;
        if (sniffed.kind() == MediaKind.IMAGE) {
            int[] size = store.imageDimensions(bytes);
            if (size != null) {
                width = size[0];
                height = size[1];
            }
        }

        String storedName = store.write(bytes, sniffed);

        Attachment saved;
        try {
            saved = attachments.save(new Attachment(
                    issue,
                    uploader,
                    sniffed.kind(),
                    sniffed.contentType(),
                    bytes.length,
                    storedName,
                    file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename(),
                    width,
                    height));
            attachments.flush();
        } catch (RuntimeException e) {
            // The file is already on disk at this point. If the row cannot be
            // written, take the file back out rather than leaving an orphan
            // nothing will ever reference or clean up.
            store.delete(storedName);
            throw e;
        }

        issue.setAttachmentCount((int) attachments.countByIssueId(issueId));
        issues.save(issue);

        return toResponse(saved, uploader);
    }

    @Transactional
    public void remove(Long attachmentId, AppUser actor) {
        Attachment attachment = attachments.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("There is no attachment with id " + attachmentId));

        if (!mayDelete(attachment, actor)) {
            throw new ForbiddenException("Only the person who added that file, or a moderator, can remove it.");
        }

        // Read everything needed off the entity before it is removed, rather
        // than reaching through a deleted object afterwards.
        Issue issue = attachment.getIssue();
        Long issueId = issue.getId();
        String storedName = attachment.getStoredName();

        attachments.delete(attachment);
        attachments.flush();

        issue.setAttachmentCount((int) attachments.countByIssueId(issueId));
        issues.save(issue);

        // Last, and deliberately after the row is gone: a file with no row is
        // invisible, a row with no file renders as a broken image.
        store.delete(storedName);
    }

    @Transactional(readOnly = true)
    public Attachment require(Long attachmentId) {
        return attachments.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("There is no attachment with id " + attachmentId));
    }

    private boolean mayDelete(Attachment attachment, AppUser actor) {
        if (actor == null) {
            return false;
        }
        if (actor.getRole() == Role.MODERATOR || actor.getRole() == Role.ADMIN) {
            return true;
        }
        return attachment.getUploader().getId().equals(actor.getId());
    }

    private byte[] readAll(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the upload", e);
        }
    }

    private AttachmentResponse toResponse(Attachment attachment, AppUser viewer) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getKind().name(),
                attachment.getKind().getLabel(),
                attachment.getContentType(),
                attachment.getByteSize(),
                attachment.getOriginalName(),
                attachment.getWidth(),
                attachment.getHeight(),
                "/api/attachments/" + attachment.getId() + "/file",
                attachment.getUploader().getDisplayName(),
                attachment.getCreatedAt(),
                mayDelete(attachment, viewer)
        );
    }
}
