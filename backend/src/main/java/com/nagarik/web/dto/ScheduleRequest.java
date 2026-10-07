package com.nagarik.web.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * A moderator committing to a date, with an optional word about why.
 *
 * Used both for acknowledging a fresh report and for revising the date later,
 * because the two carry exactly the same information - the difference is which
 * state the report is in, and that belongs to the service rather than the shape
 * of the request.
 *
 * The date is required. Acknowledging without one would be the same gesture the
 * board already has a word for, and a report that has been "seen" with no date
 * attached tells a resident nothing they did not know.
 */
public record ScheduleRequest(

        @NotNull(message = "Give a date the work should be done by")
        @FutureOrPresent(message = "That date has already passed - pick today or later")
        LocalDate etaDate,

        @Size(max = 500, message = "Keep the note under 500 characters")
        String note
) {
    /**
     * Normalizes the note on the way in, so the rest of the application only
     * ever sees either real text or null - never the empty string, and never
     * text with accidental whitespace around it. A blank note and no note are
     * the same thing and should be stored the same way.
     */
    public ScheduleRequest {
        note = (note == null || note.isBlank()) ? null : note.trim();
    }
}
