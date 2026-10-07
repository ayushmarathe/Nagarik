package com.nagarik.web.dto;

import com.nagarik.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IssueRequest(

        @NotBlank(message = "Give the problem a short title")
        @Size(min = 5, max = 140, message = "Keep the title between 5 and 140 characters")
        String title,

        @NotBlank(message = "Describe what is wrong")
        @Size(min = 10, max = 4000, message = "Keep the description between 10 and 4000 characters")
        String description,

        @NotNull(message = "Choose a category")
        Category category,

        @NotBlank(message = "Say where the problem is")
        @Size(max = 120, message = "Keep the location under 120 characters")
        String area
) {

    /**
     * Trim before validation runs, not after. Otherwise "   ab   " satisfies a
     * five-character minimum and is then stored as two characters.
     */
    public IssueRequest {
        title = title == null ? null : title.trim();
        description = description == null ? null : description.trim();
        area = area == null ? null : area.trim();
    }
}
