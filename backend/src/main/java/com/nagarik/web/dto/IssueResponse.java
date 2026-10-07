package com.nagarik.web.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One report, as the board and the dashboard both see it.
 *
 * The verify* fields are sent to everybody rather than only to moderators. A
 * verification window that residents cannot see the terms of would be asking
 * them to trust an outcome they have no way to check, which is the opposite of
 * what the window is for - so the tally, the number of votes it needs and the
 * date it closes are all public.
 *
 * canVerify and myConfirmation are the two viewer-dependent fields: the first
 * is whether this person is entitled to answer, the second is what they already
 * said. myConfirmation is a boxed Boolean because it has three states - yes, no,
 * and not yet asked.
 */
public record IssueResponse(
        Long id,
        String title,
        String description,
        String category,
        String categoryLabel,
        String status,
        String statusLabel,
        String area,
        String author,
        Instant createdAt,
        int voteCount,
        int commentCount,
        boolean votedByMe,

        // What has been promised about fixing it.
        LocalDate etaDate,
        boolean overdue,
        String workNote,
        Instant acknowledgedAt,
        String acknowledgedBy,

        // The window in which backers say whether it really got fixed.
        Instant verifyOpenedAt,
        Instant verifyDeadline,
        int verifyQuorum,
        int confirmedCount,
        int deniedCount,
        boolean canVerify,
        Boolean myConfirmation,
        Instant resolvedAt
) {
}
