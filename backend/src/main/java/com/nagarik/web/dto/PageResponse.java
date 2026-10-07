package com.nagarik.web.dto;

import java.util.List;

/**
 * One page of results plus everything the interface needs to decide whether to
 * offer another one.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasMore
) {
}
