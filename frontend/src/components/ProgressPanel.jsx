import { dueLabel, fullDate, shortDate, timeAgo, timeLeft } from '../lib/format.js';

/**
 * Everything the council has said about a report, and the neighbourhood's
 * answer to it.
 *
 * Shown to everybody rather than only to the people who can act on it. A
 * promised date that only moderators can see is not a promise, and a
 * verification tally that only moderators can see would ask residents to trust
 * a result they cannot check - which is the one thing this panel exists to
 * avoid.
 *
 * Class names are "track" rather than "progress": .progress is already the
 * thin share-fixed bar in StandingPanel, and a second meaning for it would
 * have given this panel that bar's 6px height and clipped everything in it.
 */
export default function ProgressPanel({ issue, busy, signedIn, onConfirm, onSignIn }) {
  const promised = issue.etaDate && ['ACKNOWLEDGED', 'IN_PROGRESS', 'DISPUTED'].includes(issue.status);

  if (!promised && !issue.verifyOpenedAt && issue.status !== 'RESOLVED') {
    return (
      <div className="track track-waiting">
        <p className="track-line">
          Nobody has picked this up yet. Backing it is what moves it up the list.
        </p>
      </div>
    );
  }

  return (
    <div className="track" data-status={issue.status}>
      {promised && (
        <div className="promise" data-late={issue.overdue ? 'yes' : 'no'}>
          <span className="promise-date">
            <span className="promise-label">Promised by</span>
            <strong>{shortDate(issue.etaDate)}</strong>
          </span>
          <span className="promise-due">{dueLabel(issue.etaDate)}</span>
        </div>
      )}

      {issue.workNote && <p className="track-note">“{issue.workNote}”</p>}

      {issue.acknowledgedBy && issue.acknowledgedAt && (
        <p className="track-by">
          Picked up by {issue.acknowledgedBy} {timeAgo(issue.acknowledgedAt)}
        </p>
      )}

      {issue.status === 'VERIFYING' && (
        <Verification issue={issue} busy={busy} signedIn={signedIn} onConfirm={onConfirm} onSignIn={onSignIn} />
      )}

      {issue.status === 'RESOLVED' && <Settled issue={issue} />}

      {issue.status === 'DISPUTED' && (
        <p className="track-line track-bad">
          The work was marked done, and {issue.deniedCount} of the{' '}
          {issue.confirmedCount + issue.deniedCount} people who answered said the problem was still
          there. It is back with the ward.
        </p>
      )}
    </div>
  );
}

/**
 * The open window.
 *
 * The tally is always visible; the buttons only appear for people entitled to
 * use them. Showing a disabled control to everyone else would imply they could
 * earn the right by doing something on this screen - and while backing the
 * report is in fact that something, saying so here would read as an invitation
 * to back things in order to vote on them.
 */
function Verification({ issue, busy, signedIn, onConfirm, onSignIn }) {
  const cast = issue.confirmedCount + issue.deniedCount;
  const needed = Math.max(issue.verifyQuorum - cast, 0);
  const yesShare = cast === 0 ? 0 : Math.round((issue.confirmedCount / cast) * 100);

  return (
    <div className="verify">
      <p className="verify-head">
        The ward says this is done. The people who reported it decide whether it is.
      </p>

      <div className="verify-bar" role="img" aria-label={`${issue.confirmedCount} fixed, ${issue.deniedCount} not fixed`}>
        <span className="verify-yes" style={{ width: `${yesShare}%` }} />
        <span className="verify-no" style={{ width: `${100 - yesShare}%` }} />
      </div>

      <p className="verify-tally">
        <strong>{issue.confirmedCount}</strong> fixed · <strong>{issue.deniedCount}</strong> not fixed
        <span className="verify-sep">·</span>
        {needed > 0
          ? `${needed} more ${needed === 1 ? 'answer' : 'answers'} settles it`
          : 'enough answers to settle'}
        <span className="verify-sep">·</span>
        {timeLeft(issue.verifyDeadline)}
      </p>

      {issue.canVerify ? (
        <div className="verify-actions">
          <button
            type="button"
            className={`btn verify-btn${issue.myConfirmation === true ? ' is-chosen' : ''}`}
            disabled={busy}
            aria-pressed={issue.myConfirmation === true}
            onClick={() => onConfirm(true)}
          >
            It is fixed
          </button>
          <button
            type="button"
            className={`btn verify-btn verify-btn-no${issue.myConfirmation === false ? ' is-chosen' : ''}`}
            disabled={busy}
            aria-pressed={issue.myConfirmation === false}
            onClick={() => onConfirm(false)}
          >
            Still a problem
          </button>
          {issue.myConfirmation !== null && issue.myConfirmation !== undefined && (
            <span className="verify-changed">You can change this until the window closes.</span>
          )}
        </div>
      ) : (
        <p className="verify-locked">
          {!signedIn ? (
            <>
              <button type="button" className="linkish" onClick={onSignIn}>
                Sign in
              </button>{' '}
              — only the people backing this report can answer.
            </>
          ) : issue.votedByMe ? (
            'Your answer has been counted.'
          ) : (
            'Only the people who backed this report can say whether it was fixed.'
          )}
        </p>
      )}
    </div>
  );
}

function Settled({ issue }) {
  const cast = issue.confirmedCount + issue.deniedCount;

  return (
    <p className="track-line track-good">
      {cast === 0
        ? 'Marked done, and nobody objected during the week it was open for checking.'
        : `${issue.confirmedCount} of the ${cast} people who answered confirmed this was fixed.`}
      {issue.resolvedAt ? ` Closed ${fullDate(issue.resolvedAt)}.` : ''}
    </p>
  );
}
