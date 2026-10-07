import { useState } from 'react';
import { categoryColor } from '../lib/categories.js';
import { dueLabel, fullDate, shortDate, timeAgo, timeLeft } from '../lib/format.js';

/**
 * One report in the queue.
 *
 * The row is five columns of the things a moderator decides on - how many
 * people it affects, what was promised, whether the neighbourhood has answered
 * yet - and the description is folded away behind the title, because a queue
 * you have to scroll past prose to read is a queue nobody scans.
 *
 * The buttons offered are the transitions the server will actually accept from
 * this status. That list is duplicated from IssueService on purpose: showing a
 * button that answers 409 teaches people to distrust the interface, and the
 * alternative - asking the server what is legal - is a round trip per row.
 */
export default function QueueRow({ issue, busy, moved, onSchedule, onStart, onDone, onReopen }) {
  const [open, setOpen] = useState(false);

  const promised = issue.etaDate && ['ACKNOWLEDGED', 'IN_PROGRESS', 'DISPUTED'].includes(issue.status);
  const checking = issue.status === 'VERIFYING';
  const answered = issue.confirmedCount + issue.deniedCount;

  return (
    <article
      className={`adm-row${open ? ' is-open' : ''}${moved ? ' is-moved' : ''}`}
      style={{ '--stripe': categoryColor(issue.category) }}
      data-status={issue.status}
    >
      <div className="adm-cell adm-cell-what">
        <button
          type="button"
          className="adm-title"
          aria-expanded={open}
          onClick={() => setOpen((shown) => !shown)}
        >
          {issue.title}
        </button>

        <div className="adm-meta">
          <span className="status" data-status={issue.status}>
            {issue.statusLabel}
          </span>
          <span className="tag" style={{ '--stripe': categoryColor(issue.category) }}>
            {issue.categoryLabel}
          </span>
          <span className="adm-where">{issue.area}</span>
          <span className="adm-when">{timeAgo(issue.createdAt)}</span>
        </div>

        {moved && (
          <p className="adm-moved">
            Done — this leaves the tab next time the queue is read.
          </p>
        )}
      </div>

      <div className="adm-cell adm-cell-backing">
        <span className="adm-backing-count">{issue.voteCount}</span>
        <span className="adm-cell-note">
          {issue.voteCount === 1 ? 'backer' : 'backers'}
          {issue.commentCount > 0 ? ` · ${issue.commentCount} replies` : ''}
        </span>
      </div>

      <div className="adm-cell adm-cell-eta" data-late={issue.overdue ? 'yes' : 'no'}>
        {promised ? (
          <>
            <span className="adm-eta-date">{shortDate(issue.etaDate)}</span>
            <span className="adm-cell-note">{dueLabel(issue.etaDate)}</span>
          </>
        ) : (
          <span className="adm-blank" aria-label="No date promised">
            —
          </span>
        )}
      </div>

      <div className="adm-cell adm-cell-check">
        {checking ? (
          <>
            <span className="adm-check-tally">
              {issue.confirmedCount} / {answered || 0}
            </span>
            <span className="adm-cell-note">
              said fixed · {timeLeft(issue.verifyDeadline)}
            </span>
          </>
        ) : issue.status === 'RESOLVED' || issue.status === 'DISPUTED' ? (
          <>
            <span className="adm-check-tally">
              {issue.confirmedCount} / {answered || 0}
            </span>
            <span className="adm-cell-note">
              {issue.status === 'RESOLVED' ? 'confirmed it' : 'said it was fixed'}
            </span>
          </>
        ) : (
          <span className="adm-blank" aria-label="Not being checked">
            —
          </span>
        )}
      </div>

      <div className="adm-cell adm-cell-do">
        <Actions
          issue={issue}
          busy={busy}
          onSchedule={onSchedule}
          onStart={onStart}
          onDone={onDone}
          onReopen={onReopen}
        />
      </div>

      {open && (
        <div className="adm-expand">
          <p className="adm-expand-body">{issue.description}</p>

          <dl className="adm-facts">
            <div>
              <dt>Raised by</dt>
              <dd>
                {issue.author} on {fullDate(issue.createdAt)}
              </dd>
            </div>

            {issue.acknowledgedBy && issue.acknowledgedAt && (
              <div>
                <dt>Picked up by</dt>
                <dd>
                  {issue.acknowledgedBy}, {timeAgo(issue.acknowledgedAt)}
                </dd>
              </div>
            )}

            {issue.workNote && (
              <div>
                <dt>Note to residents</dt>
                <dd>“{issue.workNote}”</dd>
              </div>
            )}

            {issue.verifyOpenedAt && (
              <div>
                <dt>Checking</dt>
                <dd>
                  Opened {timeAgo(issue.verifyOpenedAt)} · {issue.confirmedCount} fixed,{' '}
                  {issue.deniedCount} not fixed
                  {issue.verifyQuorum > 0 ? ` · needs ${issue.verifyQuorum} to settle early` : ''}
                </dd>
              </div>
            )}

            {issue.resolvedAt && (
              <div>
                <dt>Closed</dt>
                <dd>{fullDate(issue.resolvedAt)}</dd>
              </div>
            )}
          </dl>

          {/*
            Straight into the public thread. Most of what a moderator needs to
            decide anything - a second street affected, a photo described, the
            name of whoever already called the office - is written there by
            residents, not in the original report.
          */}
          <a className="adm-open" href={`/?issue=${issue.id}`} target="_blank" rel="noreferrer">
            Read the discussion on the board
          </a>
        </div>
      )}
    </article>
  );
}

function Actions({ issue, busy, onSchedule, onStart, onDone, onReopen }) {
  switch (issue.status) {
    case 'REPORTED':
      return (
        <button
          type="button"
          className="btn btn-primary btn-sm"
          disabled={busy}
          onClick={() => onSchedule('acknowledge')}
        >
          Pick up
        </button>
      );

    case 'ACKNOWLEDGED':
      return (
        <>
          <button type="button" className="btn btn-sm" disabled={busy} onClick={onStart}>
            Crew is out
          </button>
          <button type="button" className="btn btn-primary btn-sm" disabled={busy} onClick={onDone}>
            Mark done
          </button>
          <button
            type="button"
            className="btn btn-quiet btn-sm"
            disabled={busy}
            onClick={() => onSchedule('schedule')}
          >
            Change date
          </button>
        </>
      );

    case 'IN_PROGRESS':
      return (
        <>
          <button type="button" className="btn btn-primary btn-sm" disabled={busy} onClick={onDone}>
            Mark done
          </button>
          <button
            type="button"
            className="btn btn-quiet btn-sm"
            disabled={busy}
            onClick={() => onSchedule('schedule')}
          >
            Change date
          </button>
        </>
      );

    // Nothing for a moderator to do here, and that is the point. The report is
    // with the people who backed it until they answer or the week runs out.
    case 'VERIFYING':
      return <span className="adm-wait">Waiting on residents</span>;

    case 'DISPUTED':
      return (
        <>
          <button type="button" className="btn btn-primary btn-sm" disabled={busy} onClick={onStart}>
            Go again
          </button>
          <button
            type="button"
            className="btn btn-quiet btn-sm"
            disabled={busy}
            onClick={() => onSchedule('schedule')}
          >
            New date
          </button>
        </>
      );

    case 'RESOLVED':
      return (
        <button type="button" className="btn btn-quiet btn-sm" disabled={busy} onClick={onReopen}>
          It came back
        </button>
      );

    default:
      return null;
  }
}
