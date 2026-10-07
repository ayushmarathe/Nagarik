package com.nagarik.service;

import com.nagarik.domain.AppUser;
import com.nagarik.domain.Category;
import com.nagarik.domain.Confirmation;
import com.nagarik.domain.Issue;
import com.nagarik.domain.IssueStatus;
import com.nagarik.domain.Vote;
import com.nagarik.error.ConflictException;
import com.nagarik.error.ForbiddenException;
import com.nagarik.error.NotFoundException;
import com.nagarik.repo.AppUserRepository;
import com.nagarik.repo.CategoryTally;
import com.nagarik.repo.ConfirmationRepository;
import com.nagarik.repo.IssueRepository;
import com.nagarik.repo.IssueSpecs;
import com.nagarik.repo.StatusTally;
import com.nagarik.repo.VoteRepository;
import com.nagarik.web.dto.AdminSummaryResponse;
import com.nagarik.web.dto.IssueRequest;
import com.nagarik.web.dto.IssueResponse;
import com.nagarik.web.dto.PageResponse;
import com.nagarik.web.dto.ScheduleRequest;
import com.nagarik.web.dto.StatsResponse;
import com.nagarik.web.dto.TallyResponse;
import com.nagarik.web.dto.VoteResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class IssueService {

    /** Enough to fill a tall screen without making the first paint wait. */
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * How many backers have to weigh in before a verification window can close
     * early, and how long it stays open regardless.
     *
     * The quorum is min(CAP, backers), so a report backed by three people needs
     * all three and one backed by fifty needs five. Asking everybody on a small
     * report sounds strict, and would be if it were the only way out - but the
     * deadline below is, so the strictness only ever costs time, never an
     * outcome. The alternative, a fixed five, would leave every report with four
     * or fewer backers unable to settle early at all.
     */
    private static final int VERIFY_QUORUM_CAP = 5;
    private static final Duration VERIFY_WINDOW = Duration.ofDays(7);

    /** The states from which a moderator can still claim the work is finished. */
    private static final Set<IssueStatus> CAN_MARK_DONE =
            EnumSet.of(IssueStatus.ACKNOWLEDGED, IssueStatus.IN_PROGRESS);

    private final IssueRepository issues;
    private final VoteRepository votes;
    private final ConfirmationRepository confirmations;
    private final AppUserRepository users;
    private final VerificationSweeper sweeper;

    public IssueService(IssueRepository issues,
                        VoteRepository votes,
                        ConfirmationRepository confirmations,
                        AppUserRepository users,
                        VerificationSweeper sweeper) {
        this.issues = issues;
        this.votes = votes;
        this.confirmations = confirmations;
        this.users = users;
        this.sweeper = sweeper;
    }

    // ---- Reading -----------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<IssueResponse> list(Category category, IssueStatus status, String area,
                                            String search, String sortKey, int page, int size,
                                            Long viewerId) {
        return page(IssueSpecs.matching(category, status, area, search), sortKey, page, size, viewerId);
    }

    /**
     * The dashboard's listing. Same engine as the public feed with two filters
     * the public board has no use for: overdue-only, and a set of states rather
     * than one, so "everything still owed" is a single request.
     */
    @Transactional(readOnly = true)
    public PageResponse<IssueResponse> adminList(Category category, IssueStatus status, String area,
                                                 String search, boolean overdueOnly,
                                                 Collection<IssueStatus> anyOf,
                                                 String sortKey, int page, int size, Long viewerId) {
        Specification<Issue> spec = IssueSpecs.matching(category, status, area, search);
        if (anyOf != null && !anyOf.isEmpty()) {
            spec = spec.and(IssueSpecs.statusIn(anyOf));
        }
        if (overdueOnly) {
            spec = spec.and(IssueSpecs.overdueOn(LocalDate.now()));
        }
        return page(spec, sortKey, page, size, viewerId);
    }

    private PageResponse<IssueResponse> page(Specification<Issue> spec, String sortKey,
                                             int page, int size, Long viewerId) {
        // Before anything is loaded, not after: the sweep clears the persistence
        // context, which would detach a page read ahead of it.
        sweeper.sweepIfDue();

        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), sortFor(sortKey));
        Page<Issue> found = issues.findAll(spec, pageable);

        List<Long> ids = found.getContent().stream().map(Issue::getId).toList();
        Set<Long> backed = votedIssueIds(viewerId, ids);
        Map<Long, Boolean> answered = myConfirmations(viewerId, ids);

        List<IssueResponse> rows = found.getContent().stream()
                .map(issue -> toResponse(
                        issue,
                        backed.contains(issue.getId()),
                        answered.get(issue.getId())))
                .toList();

        return new PageResponse<>(
                rows,
                found.getNumber(),
                found.getSize(),
                found.getTotalElements(),
                found.getTotalPages(),
                found.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public IssueResponse get(Long id, Long viewerId) {
        sweeper.sweepIfDue();

        Issue issue = require(id);
        boolean backed = viewerId != null && votes.findByIssueIdAndUserId(id, viewerId).isPresent();
        Boolean answered = viewerId == null
                ? null
                : confirmations.findByIssueIdAndUserId(id, viewerId).map(Confirmation::isFixed).orElse(null);
        return toResponse(issue, backed, answered);
    }

    @Transactional(readOnly = true)
    public List<String> areas() {
        return issues.findDistinctAreas();
    }

    // ---- Writing as a resident ---------------------------------------------

    @Transactional
    public IssueResponse create(IssueRequest request, AppUser author) {
        // IssueRequest has already trimmed its strings.
        Issue issue = new Issue(
                request.title(),
                request.description(),
                request.category(),
                request.area(),
                author
        );
        return toResponse(issues.save(issue), false, null);
    }

    /**
     * One click backs a report, a second click takes the backing away. The vote
     * row is the source of truth; the counter on the issue is recomputed from it
     * rather than incremented, so it cannot drift out of step.
     */
    @Transactional
    public VoteResponse toggleVote(Long issueId, AppUser user) {
        Issue issue = require(issueId);

        Optional<Vote> existing = votes.findByIssueIdAndUserId(issueId, user.getId());
        boolean voted;
        if (existing.isPresent()) {
            votes.delete(existing.get());
            voted = false;
        } else {
            votes.save(new Vote(issue, user));
            voted = true;
        }
        votes.flush();

        int count = (int) votes.countByIssueId(issueId);
        issue.setVoteCount(count);
        issues.save(issue);

        return new VoteResponse(issueId, count, voted);
    }

    /**
     * A backer saying whether the repair actually happened.
     *
     * Restricted to people who backed the report, which is the whole point: they
     * are the ones who said the problem was real, so they are the ones in a
     * position to notice it is gone. Eligibility is read from the vote table at
     * the moment of answering rather than from a snapshot, so somebody who
     * withdraws their backing also gives up the vote on the fix.
     *
     * Answers can be changed while the window is open, so this is an upsert. The
     * two counters are recomputed from the confirmation rows for the same reason
     * the vote counter is - a tally that is incremented can drift, a tally that
     * is recounted cannot.
     */
    @Transactional
    public IssueResponse confirmFix(Long issueId, boolean fixed, AppUser user) {
        Issue issue = require(issueId);

        if (issue.getStatus() != IssueStatus.VERIFYING) {
            throw new ConflictException(
                    "This report is not being checked right now, so there is nothing to confirm");
        }
        if (votes.findByIssueIdAndUserId(issueId, user.getId()).isEmpty()) {
            throw new ForbiddenException(
                    "Only the people who backed this report can say whether it was fixed");
        }

        Optional<Confirmation> existing = confirmations.findByIssueIdAndUserId(issueId, user.getId());
        if (existing.isPresent()) {
            existing.get().setFixed(fixed);
        } else {
            confirmations.save(new Confirmation(issue, user, fixed));
        }
        confirmations.flush();

        recount(issue);
        settleIfDecided(issue);
        issues.save(issue);

        return toResponse(issue, true, fixed);
    }

    // ---- Writing as a moderator -------------------------------------------

    /**
     * Seen, and dated. The first thing a resident is waiting for.
     */
    @Transactional
    public IssueResponse acknowledge(Long id, ScheduleRequest request, AppUser mover) {
        Issue issue = moderated(id, mover);

        if (issue.getStatus() != IssueStatus.REPORTED) {
            throw new ConflictException(
                    "This report has already been picked up. Change the target date instead.");
        }

        issue.setStatus(IssueStatus.ACKNOWLEDGED);
        issue.setEtaDate(request.etaDate());
        issue.setWorkNote(request.note());
        issue.setAcknowledgedAt(Instant.now());
        issue.setAcknowledgedBy(mover);
        issues.save(issue);

        return asMover(issue, mover);
    }

    /**
     * Revising the promise. Allowed while work is still owed, which includes a
     * disputed report - a missed repair needs a new date more than most.
     */
    @Transactional
    public IssueResponse reschedule(Long id, ScheduleRequest request, AppUser mover) {
        Issue issue = moderated(id, mover);

        if (!issue.getStatus().acceptsEta()) {
            throw new ConflictException(
                    "A target date only applies while work is outstanding");
        }

        issue.setEtaDate(request.etaDate());
        issue.setWorkNote(request.note());
        issues.save(issue);

        return asMover(issue, mover);
    }

    /** Crew is out. Reachable from acknowledged, or from a disputed second attempt. */
    @Transactional
    public IssueResponse startWork(Long id, AppUser mover) {
        Issue issue = moderated(id, mover);

        IssueStatus from = issue.getStatus();
        if (from != IssueStatus.ACKNOWLEDGED && from != IssueStatus.DISPUTED) {
            throw new ConflictException(
                    "Work can only start on a report that has been acknowledged");
        }

        if (from == IssueStatus.DISPUTED) {
            // A second attempt starts from a clean slate - see reopen.
            confirmations.deleteForIssue(id);
            issue.clearVerification();
        }

        issue.setStatus(IssueStatus.IN_PROGRESS);
        issues.save(issue);

        return asMover(issue, mover);
    }

    /**
     * The moderator's claim that the work is finished - which is a claim, not a
     * conclusion. It opens the window instead of closing the report.
     *
     * Reachable straight from ACKNOWLEDGED as well as IN_PROGRESS, because
     * plenty of small jobs get done without anybody pausing to announce that
     * they started.
     */
    @Transactional
    public IssueResponse markDone(Long id, AppUser mover) {
        Issue issue = moderated(id, mover);

        if (!CAN_MARK_DONE.contains(issue.getStatus())) {
            throw new ConflictException(
                    "Only a report that is acknowledged or in progress can be marked done");
        }

        // Any rows left from a window that already failed would count towards
        // this one. Clear them before the snapshot, not after.
        confirmations.deleteForIssue(id);
        issue.clearVerification();

        Instant now = Instant.now();
        issue.setStatus(IssueStatus.VERIFYING);
        issue.setVerifyOpenedAt(now);
        issue.setVerifyDeadline(now.plus(VERIFY_WINDOW));
        issue.setVerifyQuorum(Math.min(VERIFY_QUORUM_CAP, Math.max(issue.getVoteCount(), 0)));
        issue.setResolvedAt(null);
        issues.save(issue);

        return asMover(issue, mover);
    }

    /**
     * Puts a settled report back on the pile - either because the neighbourhood
     * rejected the repair, or because a fixed problem came back.
     *
     * Lands on IN_PROGRESS rather than REPORTED. The report has already been
     * acknowledged once and sending it back to the start would throw away the
     * date and the note, leaving residents watching a report they know was
     * looked at claim that nobody has looked at it.
     */
    @Transactional
    public IssueResponse reopen(Long id, AppUser mover) {
        Issue issue = moderated(id, mover);

        IssueStatus from = issue.getStatus();
        if (from != IssueStatus.DISPUTED && from != IssueStatus.RESOLVED) {
            throw new ConflictException("Only a settled report can be reopened");
        }

        confirmations.deleteForIssue(id);
        issue.clearVerification();
        issue.setResolvedAt(null);
        issue.setStatus(IssueStatus.IN_PROGRESS);
        issues.save(issue);

        return asMover(issue, mover);
    }

    // ---- Tallies -----------------------------------------------------------

    /**
     * Every count the board shows, in one round trip. Categories and statuses
     * with no reports are included at zero - a filter that vanishes when empty
     * is more confusing than one that reads "0".
     */
    @Transactional(readOnly = true)
    public StatsResponse stats() {
        sweeper.sweepIfDue();

        long total = issues.count();
        long resolved = issues.countByStatus(IssueStatus.RESOLVED);

        return new StatsResponse(
                total,
                total - resolved,
                resolved,
                votes.count(),
                users.count(),
                categoryTallies(),
                statusTallies()
        );
    }

    @Transactional(readOnly = true)
    public AdminSummaryResponse adminSummary(AppUser mover) {
        requireModerator(mover);
        sweeper.sweepIfDue();

        long total = issues.count();
        long resolved = issues.countByStatus(IssueStatus.RESOLVED);

        return new AdminSummaryResponse(
                total,
                total - resolved,
                resolved,
                issues.countByStatus(IssueStatus.REPORTED),
                issues.count(IssueSpecs.overdueOn(LocalDate.now())),
                issues.countByStatus(IssueStatus.VERIFYING),
                issues.countByStatus(IssueStatus.DISPUTED),
                statusTallies(),
                categoryTallies()
        );
    }

    private List<TallyResponse> categoryTallies() {
        Map<Category, Long> counts = new EnumMap<>(Category.class);
        for (CategoryTally tally : issues.tallyByCategory()) {
            counts.put(tally.getCategory(), tally.getTotal());
        }
        return Arrays.stream(Category.values())
                .map(category -> new TallyResponse(
                        category.name(),
                        category.getLabel(),
                        counts.getOrDefault(category, 0L)))
                .toList();
    }

    private List<TallyResponse> statusTallies() {
        Map<IssueStatus, Long> counts = new EnumMap<>(IssueStatus.class);
        for (StatusTally tally : issues.tallyByStatus()) {
            counts.put(tally.getStatus(), tally.getTotal());
        }
        return Arrays.stream(IssueStatus.values())
                .map(status -> new TallyResponse(
                        status.name(),
                        status.getLabel(),
                        counts.getOrDefault(status, 0L)))
                .toList();
    }

    // ---- Internals ---------------------------------------------------------

    private Issue require(Long id) {
        return issues.findById(id)
                .orElseThrow(() -> new NotFoundException("There is no report with id " + id));
    }

    /**
     * The one place the moderator rule is written down.
     *
     * The role comes from the freshly loaded account rather than from the token's
     * claims, so revoking a moderator takes effect on their next request instead
     * of whenever their token happens to expire.
     */
    public void requireModerator(AppUser mover) {
        if (!mover.getRole().canModerate()) {
            throw new ForbiddenException("Only a moderator can do that");
        }
    }

    /**
     * Loads a report for a moderator action, refusing anybody who is not one.
     *
     * The role check lives here as well as on the route, and this is the copy
     * that matters: a route annotation only protects the routes somebody
     * remembered to annotate, whereas every path through this method is checked
     * no matter who adds the next one.
     */
    private Issue moderated(Long id, AppUser mover) {
        requireModerator(mover);
        return require(id);
    }

    /** A moderator's own view of a report they just changed. */
    private IssueResponse asMover(Issue issue, AppUser mover) {
        boolean backed = votes.findByIssueIdAndUserId(issue.getId(), mover.getId()).isPresent();
        Boolean answered = confirmations.findByIssueIdAndUserId(issue.getId(), mover.getId())
                .map(Confirmation::isFixed)
                .orElse(null);
        return toResponse(issue, backed, answered);
    }

    private void recount(Issue issue) {
        Long id = issue.getId();
        issue.setConfirmedCount((int) confirmations.countByIssueIdAndFixed(id, true));
        issue.setDeniedCount((int) confirmations.countByIssueIdAndFixed(id, false));
    }

    /**
     * Closes the window the moment enough backers agree with each other.
     *
     * A tie is not enough, even at quorum: five people split three-two have
     * decided something, five split two-two-and-waiting have not. Ties are left
     * for the deadline sweep, which breaks them in favour of the moderator's
     * claim on the grounds that the neighbourhood was given a week to overturn
     * it and did not manage to.
     */
    private void settleIfDecided(Issue issue) {
        int confirmed = issue.getConfirmedCount();
        int denied = issue.getDeniedCount();
        int quorum = issue.getVerifyQuorum();

        boolean reachedQuorum = quorum > 0 && (confirmed + denied) >= quorum;
        if (!reachedQuorum || confirmed == denied) {
            return;
        }

        if (confirmed > denied) {
            issue.setStatus(IssueStatus.RESOLVED);
            issue.setResolvedAt(Instant.now());
        } else {
            issue.setStatus(IssueStatus.DISPUTED);
            issue.setResolvedAt(null);
        }
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * Every sort ends with a tiebreaker on id. Without one, rows holding equal
     * vote counts can come back in a different order on each page of the same
     * query, which shows up as a report appearing twice while paging.
     */
    private Sort sortFor(String key) {
        if ("new".equalsIgnoreCase(key)) {
            return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        }
        if ("oldest".equalsIgnoreCase(key)) {
            return Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
        }
        if ("discussed".equalsIgnoreCase(key)) {
            return Sort.by(Sort.Order.desc("commentCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        }
        if ("eta".equalsIgnoreCase(key)) {
            // Nulls first, because a report with no date is the one most in need
            // of attention - not the one to bury at the bottom of the queue.
            return Sort.by(
                    Sort.Order.asc("etaDate").nullsFirst(),
                    Sort.Order.desc("voteCount"),
                    Sort.Order.desc("id"));
        }
        if ("deadline".equalsIgnoreCase(key)) {
            return Sort.by(
                    Sort.Order.asc("verifyDeadline").nullsLast(),
                    Sort.Order.desc("id"));
        }
        return Sort.by(Sort.Order.desc("voteCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    }

    private Set<Long> votedIssueIds(Long viewerId, List<Long> issueIds) {
        if (viewerId == null || issueIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(votes.findVotedIssueIds(viewerId, issueIds));
    }

    /** This viewer's existing answers, keyed by report. Absent means not asked yet. */
    private Map<Long, Boolean> myConfirmations(Long viewerId, List<Long> issueIds) {
        if (viewerId == null || issueIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Boolean> mine = new HashMap<>();
        for (Confirmation row : confirmations.findMineFor(viewerId, issueIds)) {
            mine.put(row.getIssue().getId(), row.isFixed());
        }
        return mine;
    }

    private IssueResponse toResponse(Issue issue, boolean votedByMe, Boolean myConfirmation) {
        AppUser acknowledgedBy = issue.getAcknowledgedBy();

        return new IssueResponse(
                issue.getId(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getCategory().name(),
                issue.getCategory().getLabel(),
                issue.getStatus().name(),
                issue.getStatus().getLabel(),
                issue.getArea(),
                issue.getAuthor().getDisplayName(),
                issue.getCreatedAt(),
                issue.getVoteCount(),
                issue.getCommentCount(),
                votedByMe,
                issue.getEtaDate(),
                issue.isOverdue(LocalDate.now()),
                issue.getWorkNote(),
                issue.getAcknowledgedAt(),
                acknowledgedBy == null ? null : acknowledgedBy.getDisplayName(),
                issue.getVerifyOpenedAt(),
                issue.getVerifyDeadline(),
                issue.getVerifyQuorum(),
                issue.getConfirmedCount(),
                issue.getDeniedCount(),
                issue.getStatus() == IssueStatus.VERIFYING && votedByMe,
                myConfirmation,
                issue.getResolvedAt()
        );
    }
}
