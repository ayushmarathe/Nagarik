package com.nagarik.web;

import com.nagarik.security.AuthPrincipal;
import com.nagarik.service.CommentService;
import com.nagarik.service.UserService;
import com.nagarik.web.dto.CommentRequest;
import com.nagarik.web.dto.CommentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/issues/{issueId}/comments")
public class CommentController {

    private final CommentService commentService;
    private final UserService userService;

    public CommentController(CommentService commentService, UserService userService) {
        this.commentService = commentService;
        this.userService = userService;
    }

    /** Public: a discussion nobody can read without an account is not a discussion. */
    @GetMapping
    public List<CommentResponse> list(@PathVariable Long issueId) {
        return commentService.list(issueId);
    }

    /** Replying needs an account, because it is attributed. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse add(@PathVariable Long issueId,
                               @Valid @RequestBody CommentRequest request,
                               @AuthenticationPrincipal AuthPrincipal principal) {
        return commentService.add(issueId, userService.require(principal), request.body());
    }
}
