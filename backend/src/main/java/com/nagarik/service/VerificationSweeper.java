package com.nagarik.service;

import com.nagarik.repo.IssueRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Closes verification windows whose deadline has passed.
 *
 * There is deliberately no @Scheduled here, and no @EnableScheduling anywhere.
 * A timer only settles windows while the application happens to be running, so
 * a laptop that was closed over the weekend would come back with reports still
 * sitting in a window that expired on Saturday. Sweeping on the way into a read
 * has the opposite property: whenever anybody looks, what they are about to
 * read has already been brought up to date, no matter how long the process was
 * down. The work is two indexed bulk updates that usually match nothing.
 *
 * The throttle keeps that from turning into two writes on every page load.
 * Deadlines are seven days out, so settling within half a minute of one is far
 * more punctuality than the feature needs.
 */
@Service
public class VerificationSweeper {

    private static final Duration MIN_GAP = Duration.ofSeconds(30);

    private final IssueRepository issues;

    /**
     * Milliseconds since the epoch of the last sweep. Starts at zero so the
     * first request after startup always sweeps, which is exactly when there is
     * most likely to be a backlog.
     */
    private final AtomicLong lastSweptAt = new AtomicLong(0L);

    public VerificationSweeper(IssueRepository issues) {
        this.issues = issues;
    }

    /**
     * Sweep, unless somebody already did recently.
     *
     * Callers must invoke this before loading any Issue of their own: the
     * underlying updates clear the persistence context, which would detach
     * entities already read in the surrounding transaction.
     */
    public void sweepIfDue() {
        long now = System.currentTimeMillis();
        long previous = lastSweptAt.get();

        if (now - previous < MIN_GAP.toMillis()) {
            return;
        }
        // Whoever wins the swap does the work. A loser under concurrent load
        // skips rather than queues, because the winner's sweep covers the same
        // rows the loser would have looked at.
        if (!lastSweptAt.compareAndSet(previous, now)) {
            return;
        }

        sweep();
    }

    /**
     * Settles every expired window regardless of the throttle. Used after a
     * moderator acts, where the dashboard is about to be re-read and should not
     * show a state the moderator's own click just invalidated.
     *
     * @return how many reports changed state
     */
    public int sweep() {
        Instant now = Instant.now();
        lastSweptAt.set(System.currentTimeMillis());

        // Order is irrelevant - the two statements match disjoint sets of rows,
        // split on whether confirmations outnumber denials.
        int resolved = issues.settleExpiredAsResolved(now);
        int disputed = issues.settleExpiredAsDisputed(now);
        return resolved + disputed;
    }
}
