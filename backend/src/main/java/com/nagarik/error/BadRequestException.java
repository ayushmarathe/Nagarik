package com.nagarik.error;

/**
 * The request was understood and refused on its merits - a file type we do not
 * accept, a file over the cap, too many attachments on one report.
 *
 * Distinct from ConflictException, which is for a request that collides with
 * state that already exists. This one maps to 400.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
