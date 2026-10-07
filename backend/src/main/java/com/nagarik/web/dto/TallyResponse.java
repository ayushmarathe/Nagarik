package com.nagarik.web.dto;

/**
 * A count against one category or one status. The label travels with it so the
 * interface never has to keep its own copy of the wording.
 */
public record TallyResponse(String value, String label, long total) {
}
