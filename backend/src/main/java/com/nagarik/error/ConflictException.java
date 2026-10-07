package com.nagarik.error;

/**
 * Something was well-formed but clashed with what is already stored - an email
 * that already has an account, a display name already taken.
 *
 * Distinct from DataIntegrityViolationException, which is the same clash
 * discovered by the database a moment later. This one exists so the message can
 * name the field; that one is the backstop for two people registering at the
 * same instant.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
