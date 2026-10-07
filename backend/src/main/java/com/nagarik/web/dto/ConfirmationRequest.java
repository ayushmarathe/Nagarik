package com.nagarik.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * One resident's answer to whether the work actually happened.
 *
 * A boxed Boolean with @NotNull rather than a primitive on purpose: a primitive
 * would quietly read a missing field as false, turning a malformed request into
 * a vote against the repair. Made explicit, the same request is rejected.
 */
public record ConfirmationRequest(

        @NotNull(message = "Say whether the problem is fixed")
        Boolean fixed
) {
}
