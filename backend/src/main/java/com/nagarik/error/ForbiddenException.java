package com.nagarik.error;

/**
 * The request was understood and the caller is signed in, but their role does
 * not allow this particular move - a resident trying to close someone's report.
 *
 * Separate from UnauthenticatedException on purpose: 401 means "sign in", 403
 * means "signing in again will not help". Sending 401 for a permissions problem
 * makes the interface show a sign-in box to somebody who is already signed in,
 * which is how people end up thinking the app is broken.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
