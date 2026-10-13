package com.nagarik.repo;

import com.nagarik.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /** The gallery on one report, in the order they were added. */
    List<Attachment> findByIssueIdOrderByCreatedAtAsc(Long issueId);

    /** Feeds the denormalized count on the issue row. */
    long countByIssueId(Long issueId);
}
