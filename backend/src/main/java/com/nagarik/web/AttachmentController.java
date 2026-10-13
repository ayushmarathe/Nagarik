package com.nagarik.web;

import com.nagarik.domain.AppUser;
import com.nagarik.domain.Attachment;
import com.nagarik.error.NotFoundException;
import com.nagarik.security.AuthPrincipal;
import com.nagarik.service.AttachmentService;
import com.nagarik.service.MediaStore;
import com.nagarik.service.UserService;
import com.nagarik.web.dto.AttachmentResponse;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Photos and video on a report.
 *
 * Two paths rather than one tree, because they have different owners: the
 * collection hangs off a report (/api/issues/{id}/attachments), while a single
 * file is addressed on its own (/api/attachments/{id}) so the bytes have a
 * stable URL that does not change if the file is ever moved between reports.
 */
@RestController
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final UserService userService;
    private final MediaStore store;

    public AttachmentController(AttachmentService attachmentService,
                                UserService userService,
                                MediaStore store) {
        this.attachmentService = attachmentService;
        this.userService = userService;
        this.store = store;
    }

    /** Public, like the reports and the discussion they belong to. */
    @GetMapping("/api/issues/{issueId}/attachments")
    public List<AttachmentResponse> list(@PathVariable Long issueId,
                                         @AuthenticationPrincipal AuthPrincipal principal) {
        return attachmentService.list(issueId, viewer(principal));
    }

    /** Attaching needs an account, because it is attributed and removable. */
    @PostMapping("/api/issues/{issueId}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse add(@PathVariable Long issueId,
                                  @RequestParam(value = "file", required = false) MultipartFile file,
                                  @AuthenticationPrincipal AuthPrincipal principal) {
        return attachmentService.add(issueId, userService.require(principal), file);
    }

    @DeleteMapping("/api/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id,
                       @AuthenticationPrincipal AuthPrincipal principal) {
        attachmentService.remove(id, userService.require(principal));
    }

    /**
     * The bytes.
     *
     * Honours a Range request, which is not optional for video: a browser asked
     * to seek in an <video> sends Range, and a server that answers 200 with the
     * whole file every time makes the scrubber unusable on anything large.
     *
     * Content-Type is the type the bytes were sniffed to be at upload, never
     * anything derived from the request, and nosniff stops a browser second-
     * guessing it. Together those are what keep a file uploaded as a photo from
     * ever being executed as a document on this origin.
     */
    @GetMapping("/api/attachments/{id}/file")
    public ResponseEntity<?> file(@PathVariable Long id,
                                  @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        Attachment attachment = attachmentService.require(id);

        if (!store.exists(attachment.getStoredName())) {
            // The row outlived its file. Report it as missing rather than
            // serving an empty 200, which renders as a broken image with no
            // explanation.
            throw new NotFoundException("That file is no longer stored on the server");
        }

        Resource resource = store.read(attachment.getStoredName());
        long length = store.sizeOnDisk(attachment.getStoredName());
        String filename = MediaStore.headerSafeName(attachment.getOriginalName());

        ResponseEntity.BodyBuilder shared = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                // The bytes under one id never change - a new upload gets a new
                // id - so this can be cached hard.
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());

        if (rangeHeader == null || rangeHeader.isBlank()) {
            return shared.contentLength(length).body(resource);
        }

        List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
        if (ranges.isEmpty()) {
            return shared.contentLength(length).body(resource);
        }

        // One range only. Multipart byte ranges are legal but no browser needs
        // them for media playback, and answering the first range is a valid
        // response to a multi-range request.
        ResourceRegion region = ranges.get(0).toResourceRegion(resource);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(region);
    }

    /**
     * The signed-in account, or null for an anonymous reader.
     *
     * Needed rather than UserService.require because listing is public: a reader
     * with no token still gets the gallery, just with canDelete false on every
     * entry.
     */
    private AppUser viewer(AuthPrincipal principal) {
        return principal == null ? null : userService.require(principal);
    }
}
