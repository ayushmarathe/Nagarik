package com.nagarik.web;

import com.nagarik.domain.Category;
import com.nagarik.domain.IssueStatus;
import com.nagarik.security.AuthPrincipal;
import com.nagarik.service.IssueService;
import com.nagarik.service.UserService;
import com.nagarik.web.dto.ConfirmationRequest;
import com.nagarik.web.dto.IssueRequest;
import com.nagarik.web.dto.IssueResponse;
import com.nagarik.web.dto.PageResponse;
import com.nagarik.web.dto.StatsResponse;
import com.nagarik.web.dto.VoteResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Identity now arrives as a verified token rather than as an X-User-Id header.
 *
 * The header version trusted the client to say who it was, which meant anyone
 * could vote as anyone else by editing a header. The parameter is now an
 * AuthPrincipal that only JwtAuthFilter can populate, so a null here genuinely
 * means "not signed in".
 *
 * Read routes take a nullable principal because the board is public: the only
 * thing identity changes about a read is whether your own backing shows as
 * already pressed.
 */
@RestController
@RequestMapping("/api/issues")
public class IssueController {

    private final IssueService issueService;
    private final UserService userService;

    public IssueController(IssueService issueService, UserService userService) {
        this.issueService = issueService;
        this.userService = userService;
    }

    /**
     * The feed. Every filter is optional and they combine freely.
     *
     * @param sort one of "top" (most backed), "new", or "discussed"
     * @param q    matches against title, description and locality
     */
    @GetMapping
    public PageResponse<IssueResponse> list(
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "top") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return issueService.list(category, status, area, q, sort, page, size, viewerId(principal));
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return issueService.stats();
    }

    /** The localities that actually have reports, for the locality filter. */
    @GetMapping("/areas")
    public List<String> areas() {
        return issueService.areas();
    }

    @GetMapping("/{id}")
    public IssueResponse get(@PathVariable Long id,
                             @AuthenticationPrincipal AuthPrincipal principal) {
        return issueService.get(id, viewerId(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssueResponse create(@Valid @RequestBody IssueRequest request,
                                @AuthenticationPrincipal AuthPrincipal principal) {
        return issueService.create(request, userService.require(principal));
    }

    @PostMapping("/{id}/vote")
    public VoteResponse toggleVote(@PathVariable Long id,
                                   @AuthenticationPrincipal AuthPrincipal principal) {
        return issueService.toggleVote(id, userService.require(principal));
    }

    /**
     * A backer answering whether the repair actually happened.
     *
     * This lives on the public controller rather than under /api/admin because
     * it is a resident action - the one part of the workflow a moderator cannot
     * do on their own behalf. Who is allowed to answer is decided in the service
     * by looking for their backing, not by a role.
     */
    @PostMapping("/{id}/confirm")
    public IssueResponse confirm(@PathVariable Long id,
                                 @Valid @RequestBody ConfirmationRequest request,
                                 @AuthenticationPrincipal AuthPrincipal principal) {
        return issueService.confirmFix(id, request.fixed(), userService.require(principal));
    }

    /** Null when nobody is signed in, which every read path has to tolerate. */
    private Long viewerId(AuthPrincipal principal) {
        return principal == null ? null : principal.id();
    }
}
