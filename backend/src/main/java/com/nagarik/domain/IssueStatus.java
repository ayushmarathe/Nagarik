package com.nagarik.domain;

/**
 * How far a report has travelled.
 *
 * The lifecycle exists because "resolved" is a claim, and the person making it
 * is not the person living with the problem. So a moderator cannot declare a
 * report finished on their own: marking the work done opens a window in which
 * the people who backed the report say whether it actually got fixed. Their
 * answer is what produces RESOLVED or DISPUTED.
 *
 *   REPORTED
 *      -> ACKNOWLEDGED    a moderator has seen it and committed to a date
 *      -> IN_PROGRESS     work has started
 *      -> VERIFYING       the moderator says it is done; backers are checking
 *      -> RESOLVED        they agreed
 *      -> DISPUTED        they did not, and it goes back to the queue
 *
 * DISPUTED returns to IN_PROGRESS, which can open verification a second time.
 * The order of the constants is the order the dashboard shows its columns in,
 * so keep them in lifecycle order rather than alphabetical.
 */
public enum IssueStatus {

    REPORTED("Reported"),
    ACKNOWLEDGED("Acknowledged"),
    IN_PROGRESS("Work started"),
    VERIFYING("Checking the fix"),
    RESOLVED("Fixed"),
    DISPUTED("Still not fixed");

    private final String label;

    IssueStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Is this report still somebody's problem? Everything except a confirmed fix
     * counts, DISPUTED very much included - a report the neighbourhood says is
     * unfixed is more open than one nobody has looked at.
     */
    public boolean isOpen() {
        return this != RESOLVED;
    }

    /** Is a moderator waiting on residents rather than the other way round? */
    public boolean isAwaitingVerification() {
        return this == VERIFYING;
    }

    /**
     * Can a target date be attached to a report in this state? Only while there
     * is still work to promise - once verification opens, the date has already
     * been either met or missed and editing it would rewrite history.
     */
    public boolean acceptsEta() {
        return this == ACKNOWLEDGED || this == IN_PROGRESS || this == DISPUTED;
    }
}
