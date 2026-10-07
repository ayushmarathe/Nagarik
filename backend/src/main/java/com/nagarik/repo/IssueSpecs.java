package com.nagarik.repo;

import com.nagarik.domain.Category;
import com.nagarik.domain.Issue;
import com.nagarik.domain.IssueStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the feed's where-clause out of whichever filters were actually given.
 *
 * Each filter is independent and any combination is valid, which is exactly the
 * case that a fixed set of derived queries handles badly - three optional
 * filters would need eight methods.
 */
public final class IssueSpecs {

    /**
     * The states in which a promised date is still a promise. Matches
     * IssueStatus.acceptsEta - kept as a set here because a Criteria "in"
     * predicate needs a collection, and duplicated deliberately rather than
     * derived, so that changing one forces a look at the other.
     */
    private static final Set<IssueStatus> OWES_WORK =
            EnumSet.of(IssueStatus.ACKNOWLEDGED, IssueStatus.IN_PROGRESS, IssueStatus.DISPUTED);

    private IssueSpecs() {
    }

    public static Specification<Issue> matching(Category category, IssueStatus status, String area, String search) {
        return (root, query, builder) -> {
            List<Predicate> conditions = new ArrayList<>();

            if (category != null) {
                conditions.add(builder.equal(root.get("category"), category));
            }
            if (status != null) {
                conditions.add(builder.equal(root.get("status"), status));
            }
            if (area != null && !area.isBlank()) {
                conditions.add(builder.equal(root.get("area"), area.trim()));
            }
            if (search != null && !search.isBlank()) {
                // Lowercase both sides rather than relying on the database's
                // collation, so the same search behaves the same everywhere.
                String needle = "%" + search.trim().toLowerCase() + "%";
                conditions.add(builder.or(
                        builder.like(builder.lower(root.get("title")), needle),
                        builder.like(builder.lower(root.get("description")), needle),
                        builder.like(builder.lower(root.get("area")), needle)
                ));
            }

            if (conditions.isEmpty()) {
                return builder.conjunction();
            }
            return builder.and(conditions.toArray(new Predicate[0]));
        };
    }

    /**
     * Past its promised date with the work still outstanding.
     *
     * This is the database-side twin of Issue.isOverdue, and the two have to
     * agree: the dashboard counts overdue reports with this query and then
     * labels individual rows with that method, so a disagreement would show up
     * as a count that does not match the list under it.
     */
    public static Specification<Issue> overdueOn(LocalDate today) {
        return (root, query, builder) -> builder.and(
                builder.isNotNull(root.get("etaDate")),
                builder.lessThan(root.get("etaDate"), today),
                root.get("status").in(OWES_WORK)
        );
    }

    /**
     * Restricts to a set of states. Used by the dashboard, whose default view is
     * "everything still owed" rather than a single status.
     */
    public static Specification<Issue> statusIn(Collection<IssueStatus> statuses) {
        return (root, query, builder) -> {
            if (statuses == null || statuses.isEmpty()) {
                return builder.conjunction();
            }
            return root.get("status").in(statuses);
        };
    }
}
