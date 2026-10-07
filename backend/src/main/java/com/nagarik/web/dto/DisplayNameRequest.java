package com.nagarik.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Renaming yourself. The email is the login and changing it is a separate,
 *  verified flow that does not exist yet. */
public record DisplayNameRequest(

        @NotBlank(message = "Enter a name")
        @Size(min = 2, max = 60, message = "Keep the name between 2 and 60 characters")
        String displayName
) {
    public DisplayNameRequest {
        displayName = displayName == null ? null : displayName.trim();
    }
}
