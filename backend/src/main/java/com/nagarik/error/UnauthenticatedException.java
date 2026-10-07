package com.nagarik.error;

/** Thrown when an action needs a known person and the request has none. Rendered as 401. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
