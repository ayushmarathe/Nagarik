package com.nagarik.web.dto;

import java.util.List;

/**
 * The state of the whole board in one object.
 *
 * open and resolved are pre-computed rather than left for the interface to add
 * up, because "open" means reported plus acknowledged, and that definition
 * should live in one place.
 */
public record StatsResponse(
        long total,
        long open,
        long resolved,
        long backings,
        long residents,
        List<TallyResponse> byCategory,
        List<TallyResponse> byStatus
) {
}
