package com.nagarik.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRequest(

        @NotBlank(message = "Enter a name")
        @Size(min = 2, max = 60, message = "Keep the name between 2 and 60 characters")
 String displayName
) {

 public UserRequest {
  displayName = displayName == null ? null : displayName.trim();
    }
}
