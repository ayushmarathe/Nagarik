package com.nagarik.domain;

/**
 * What kind of file is hanging off a report.
 *
 * Deliberately only two values, and deliberately not "the MIME type": the
 * interface needs to know whether to render an <img> or a <video>, and that is a
 * two-way decision. The exact MIME type is stored alongside on the attachment,
 * because that is what has to go back out in the Content-Type header.
 *
 * Labels follow Category: the wording the UI shows lives here so the two cannot
 * drift apart.
 */
public enum MediaKind {

    IMAGE("Photo"),
    VIDEO("Video");

    private final String label;

    MediaKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
