package com.nagarik.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(

        @NotBlank(message = "Write something before posting")
        @Size(max = 2000, message = "Keep replies under 2000 characters")
          String body
) {

public CommentRequest {
        body = body == null ? null : body.trim();
    }
}
