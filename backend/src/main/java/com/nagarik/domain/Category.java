package com.nagarik.domain;

/**
 * The kinds of problem a resident can report. Labels are the wording shown in
 * the UI - the frontend reads them from /api/categories so the two never drift.
 */
public enum Category {

    ELECTRICITY("Electricity"),
    WATER("Water supply"),
    ROADS("Roads and footpaths"),
    DRAINAGE("Drainage and sewage"),
    GARBAGE("Garbage collection"),
    STREETLIGHT("Street lighting"),
    OTHER("Something else");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
