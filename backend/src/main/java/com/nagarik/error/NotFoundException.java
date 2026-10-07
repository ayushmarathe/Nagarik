package com.nagarik.error;

/** Thrown when an id in the URL does not match anything. Rendered as 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
