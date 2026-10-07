package com.nagarik.web.dto;

/** Lets the frontend render category and status pickers without hardcoding the enum. */
public record OptionResponse(
        String value,
        String label
) {
}
