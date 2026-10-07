package com.nagarik.repo;

import com.nagarik.domain.Issue;
import com.nagarik.domain.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Filtering runs through JpaSpecificationExecutor rather than a stack of derived
 * queries or one JPQL string full of "(:category is null or ...)". Criteria
 * predicates are built in Java, so an absent filter simply is not added and
 * Hibernate never has to infer the type of a null enum parameter.
 */
public interface IssueRepository extends JpaRepository<Issue, Long>, JpaSpecificationExecutor<Issue> {

    long countByStatus(IssueStatus status);

    @Query("select i.category as category, count(i) as total from Issue i group by i.category")
    List<CategoryTally> tallyByCategory();

    @Query("select i.status as status, count(i) as total from Issue i group by i.status")
    List<StatusTally> tallyByStatus();

    /** Powers the locality filter. Small list - one per neighbourhood, not per report. */
    @Query("select distinct i.area from Issue i order by i.area asc")
    List<String> findDistinctAreas();

    /**
     * Closes every verification window whose deadline has passed and whose votes
     * came out in favour - including the windows nobody voted in at all, where
     * confirmed and denied are both zero and the moderator's claim stands
     * unchallenged.
     *
     * Written as a bulk update rather than a loop over entities because the only
     * inputs are columns already on the row: no report needs loading to decide
     * its own outcome. Enum literals are fully qualified, which is the form JPQL
     * accepts in every Hibernate version rather than only the recent ones.
     *
     * clearAutomatically matters here. A bulk update goes straight to the
     * database without touching the persistence context, so any Issue already
     * loaded in this transaction would otherwise keep reporting the old status.
     *
     * REQUIRES_NEW is what makes the sweep usable from a read. Most callers are
     * inside a readOnly transaction, where Hibernate sets the flush mode to
     * manual and a write is discarded in silence rather than refused - the worst
     * kind of failure. Its own transaction sidesteps that entirely. The price is
     * that the sweep must run before the caller loads anything, since
     * clearAutomatically detaches whatever the surrounding context is holding.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update Issue i
              set i.status = com.nagarik.domain.IssueStatus.RESOLVED,
                  i.resolvedAt = :now
            where i.status = com.nagarik.domain.IssueStatus.VERIFYING
              and i.verifyDeadline is not null
              and i.verifyDeadline <= :now
              and i.confirmedCount >= i.deniedCount
           """)
    int settleExpiredAsResolved(@Param("now") Instant now);

    /**
     * The other half of the sweep: windows where more people said it was not
     * fixed than said it was. A strict majority against, so a tie falls to the
     * query above and the claim survives.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update Issue i
              set i.status = com.nagarik.domain.IssueStatus.DISPUTED
            where i.status = com.nagarik.domain.IssueStatus.VERIFYING
              and i.verifyDeadline is not null
              and i.verifyDeadline <= :now
              and i.confirmedCount < i.deniedCount
           """)
    int settleExpiredAsDisputed(@Param("now") Instant now);
}
