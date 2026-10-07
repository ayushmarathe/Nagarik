package com.nagarik.web;

import com.nagarik.domain.Category;
import com.nagarik.domain.IssueStatus;
import com.nagarik.security.AuthPrincipal;
import com.nagarik.service.IssueService;
import com.nagarik.service.UserService;
import com.nagarik.web.dto.AdminSummaryResponse;
import com.nagarik.web.dto.IssueResponse;
import com.nagarik.web.dto.PageResponse;
import com.nagarik.web.dto.ScheduleRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.Set;

/**
 * The moderator's half of the application.
 *
 * Kept on its own prefix so the whole surface can be locked with a single rule
 * in SecurityConfig - /api/admin/** needs a moderator, no exceptions and nothing
 * to overlook when a route is added later. That is also why the transitions live
 * here rather than alongside the public routes they operate on: grouping them by
 * who may call them makes the rule possible, grouping them by subject would mean
 * a per-route list that somebody eventually forgets to extend.
 *
 * Each transition is its own verb rather than one endpoint taking a target
 * status. A free-form setter would let a report jump from Reported straight to
 * Fixed, skipping the verification window entirely - which is the one thing the
 * window exists to prevent. Named verbs make every legal move explicit and every
 * illegal one impossible to express.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    /**
     * The dashboard's tabs, as sets of statuses.
     *
     * Defined here rather than on the client so the two cannot disagree about
     * what "owed" means, and so a new status has exactly one place to be slotted
     * into the queue.
     */
    private static final Set<IssueStatus> INBOX = EnumSet.of(IssueStatus.REPORTED);
    private static final Set<IssueStatus> OWED =
            EnumSet.of(IssueStatus.ACKNOWLEDGED, IssueStatus.IN_PROGRESS);
    private static final Set<IssueStatus> VERIFYING = EnumSet.of(IssueStatus.VERIFYING);
    private static final Set<IssueStatus> DISPUTED = EnumSet.of(IssueStatus.DISPUTED);
    private static final Set<IssueStatus> RESOLVED = EnumSet.of(IssueStatus.RESOLVED);
    private static final Set<IssueStatus> OPEN = EnumSet.complementOf(EnumSet.of(IssueStatus.RESOLVED));

    private final IssueService issueService;
    private final UserService userService;

    public AdminController(IssueService issueService, UserService userService) {
        this.issueService = issueService;
        this.userService = userService;
    }

    /** The counters across the top of the dashboard. */
    @GetMapping("/summary")
    public AdminSummaryResponse summary(@AuthenticationPrincipal AuthPrincipal principal) {
        return issueService.adminSummary(userService.require(principal));
    }

    /**
     * The queue.
     *
     * @param bucket  one of inbox, owed, verifying, disputed, resolved, open; absent means everything
     * @param overdue narrows to reports past their promised date
     * @param sort    adds "eta" and "deadline" to the public board's options
     */
    @GetMapping("/issues")
    public PageResponse<IssueResponse> issues(
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean overdue,
            @RequestParam(required = false, defaultValue = "eta") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "25") int size,
            @AuthenticationPrincipal AuthPrincipal principal) {

        // Loaded rather than merely trusted: the route rule already required a
        // moderator, but adminSummary and the transitions re-check the role
        // against the stored account, and the queue should not be the one place
        // that skips it.
        var mover = userService.require(principal);
        issueService.requireModerator(mover);

        return issueService.adminList(
                category, status, area, q, overdue, bucketOf(bucket), sort, page, size, mover.getId());
    }

    @PostMapping("/issues/{id}/acknowledge")
    public IssueResponse acknowledge(@PathVariable Long id,
                                     @Valid @RequestBody ScheduleRequest request,
                                     @AuthenticationPrincipal AuthPrincipal principal) {
        return issueService.acknowledge(id, request, userService.require(principal));
    }

    /** Revising a date already given. Same body, different moment. */
    @PostMapping("/issues/{id}/schedule")
    public IssueResponse schedule(@PathVariable Long id,
                                  @Valid @RequestBody ScheduleRequest request,
                                  @AuthenticationPrincipal AuthPrincipal principal) {
        return issueService.reschedule(id, request, userService.require(principal));
    }

    @PostMapping("/issues/{id}/start")
    public IssueResponse start(@PathVariable Long id,
                               @AuthenticationPrincipal AuthPrincipal principal) {
        return issueService.startWork(id, userService.require(principal));
    }

    /** Opens the verification window. Does not close the report. */
    @PostMapping("/issues/{id}/done")
    public IssueResponse done(@PathVariable Long id,
                              @AuthenticationPrincipal AuthPrincipal principal) {
        return issueService.markDone(id, userService.require(principal));
    }

    @PostMapping("/issues/{id}/reopen")
    public IssueResponse reopen(@PathVariable Long id,
                                @AuthenticationPrincipal AuthPrincipal principal) {
        return issueService.reopen(id, userService.require(principal));
    }

    /**
     * An unknown bucket name falls through to no filter rather than to an error.
     * The parameter only ever narrows a list, so the worst a typo can do is show
     * more than was asked for - not worth a 400 on a read.
     */
    private Set<IssueStatus> bucketOf(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            return Set.of();
        }
        return switch (bucket.trim().toLowerCase()) {
            case "inbox" -> INBOX;
            case "owed" -> OWED;
            case "verifying" -> VERIFYING;
            case "disputed" -> DISPUTED;
            case "resolved" -> RESOLVED;
            case "open" -> OPEN;
            default -> Set.of();
        };
    }
}
