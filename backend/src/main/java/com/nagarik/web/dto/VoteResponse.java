package com.nagarik.web.dto;

public record VoteResponse(
        Long issueId,
        int voteCount,
        boolean voted
) {
}
