package com.nagarik.repo;

import com.nagarik.domain.Confirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConfirmationRepository extends JpaRepository<Confirmation, Long> {

    Optional<Confirmation> findByIssueIdAndUserId(Long issueId, Long userId);

    long countByIssueIdAndFixed(Long issueId, boolean fixed);

    /**
     * Which of these reports has this person already answered, and how? Returns
     * whole rows rather than ids because the feed needs the direction of the
     * answer, not just its existence - a row that reads "you said it is not
     * fixed" is the useful version.
     */
    @Query("select c from Confirmation c where c.user.id = :userId and c.issue.id in :issueIds")
    List<Confirmation> findMineFor(@Param("userId") Long userId,
                                   @Param("issueIds") Collection<Long> issueIds);

    /**
     * Clears the slate when a disputed report goes back for another attempt.
     *
     * A bulk delete rather than the derived deleteByIssueId, which would load
     * every row into the session first. @Modifying with clearAutomatically is
     * needed because the statement bypasses the persistence context: without it,
     * Confirmation instances already loaded in this transaction would survive as
     * stale copies of rows that no longer exist.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Confirmation c where c.issue.id = :issueId")
    int deleteForIssue(@Param("issueId") Long issueId);
}
