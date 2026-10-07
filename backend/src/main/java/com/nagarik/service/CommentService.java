package com.nagarik.service;

import com.nagarik.domain.AppUser;
import com.nagarik.domain.Comment;
import com.nagarik.domain.Issue;
import com.nagarik.error.NotFoundException;
import com.nagarik.repo.CommentRepository;
import com.nagarik.repo.IssueRepository;
import com.nagarik.web.dto.CommentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService {

    private final CommentRepository comments;
    private final IssueRepository issues;

    public CommentService(CommentRepository comments, IssueRepository issues) {
        this.comments = comments;
        this.issues = issues;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long issueId) {
        if (!issues.existsById(issueId)) {
            throw new NotFoundException("There is no report with id " + issueId);
        }
        return comments.findByIssueIdOrderByCreatedAtAsc(issueId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CommentResponse add(Long issueId, AppUser author, String body) {
        Issue issue = issues.findById(issueId)
                .orElseThrow(() -> new NotFoundException("There is no report with id " + issueId));

        Comment saved = comments.save(new Comment(issue, author, body));
        comments.flush();

        issue.setCommentCount((int) comments.countByIssueId(issueId));
        issues.save(issue);

        return toResponse(saved);
    }

    private CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getBody(),
                comment.getAuthor().getDisplayName(),
                comment.getCreatedAt()
        );
    }
}
