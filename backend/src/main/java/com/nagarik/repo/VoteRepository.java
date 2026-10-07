package com.nagarik.repo;

import com.nagarik.domain.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    Optional<Vote> findByIssueIdAndUserId(Long issueId, Long userId);

    long countByIssueId(Long issueId);

    /**
     * Which of these issues has this person already backed? Answers the whole
     * feed in one query instead of one per row.
     */
    @Query("select v.issue.id from Vote v where v.user.id = :userId and v.issue.id in :issueIds")
    List<Long> findVotedIssueIds(@Param("userId") Long userId, @Param("issueIds") Collection<Long> issueIds);
}
