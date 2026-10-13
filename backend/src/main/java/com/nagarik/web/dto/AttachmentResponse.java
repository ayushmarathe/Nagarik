package com.nagarik.web.dto;

import java.time.Instant;

/**
 * One attached photo or video.
 *
 * url is relative on purpose. The frontend is served from the same origin as
 * the API in development through Vite's proxy and in production behind one host,
 * so a relative path works in both and does not have to be rewritten when the
 * host changes.
 *
 * width and height are null for video, and for image formats the JDK cannot
 * measure. Callers must treat them as optional.
 */
public record AttachmentResponse(
        Long id,
        String kind,
        String kindLabel,
        String contentType,
        long byteSize,
        String originalName,
        Integer width,
        Integer height,
        String url,
        String uploadedBy,
        Instant createdAt,
        boolean canDelete
) {
}
