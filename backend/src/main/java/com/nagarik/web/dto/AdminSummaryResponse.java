package com.nagarik.web.dto;

import java.util.List;

/**
 * What a moderator needs to see before deciding what to do next.
 *
 * Deliberately not the same object as StatsResponse. That one describes the
 * board to residents - how much has been raised, how much has been fixed. This
 * one is a worklist: the three numbers at the top are the three piles of work
 * that belong to whoever is looking at it, and they are counts of things to do
 * rather than things that happened.
 */
public record AdminSummaryResponse(

        long total,

        /** Anything not confirmed fixed, disputed reports included. */
        long open,

        long resolved,

        /** Reported, and nobody has committed to a date yet. */
        long unacknowledged,

        /** Promised by a date that has now passed, still owed. */
        long overdue,

        /** Marked done; waiting on the people who backed it. */
        long awaitingVerification,

        /** Residents said the work did not hold. Back on the pile. */
        long disputed,

        List<TallyResponse> byStatus,
        List<TallyResponse> byCategory
) {
}
