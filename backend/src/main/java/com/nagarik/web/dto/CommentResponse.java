package com.nagarik.web.dto;

import java.time.Instant;

public record CommentResponse(
        Long id,
        String body,
        String author,
        Instant createdAt
) {
}
