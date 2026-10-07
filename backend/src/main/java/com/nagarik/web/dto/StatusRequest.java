package com.nagarik.web.dto;

import com.nagarik.domain.IssueStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(

        @NotNull(message = "Choose a status")
        IssueStatus status
) {
}
