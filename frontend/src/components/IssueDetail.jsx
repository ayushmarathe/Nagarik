import { useEffect, useRef, useState } from 'react';
import VoteControl from './VoteControl.jsx';
import ProgressPanel from './ProgressPanel.jsx';
import { api } from '../api.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { useToast } from './Toasts.jsx';
import { categoryColor } from '../lib/categories.js';
import { fullDate, timeAgo } from '../lib/format.js';

export default function IssueDetail({ issueId, onBack, onChanged }) {
  const { user, signedIn, canModerate, requireUser } = useAuth();
  const pushToast = useToast();

  const [issue, setIssue] = useState(null);
  const [comments, setComments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [voting, setVoting] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [reply, setReply] = useState('');
  const [replyError, setReplyError] = useState(null);
  const [posting, setPosting] = useState(false);

  // Watch the id, not the object: the user object is replaced on every sign-in
  // and rename, which would reload the report each time.
  const viewerId = user?.id ?? null;

  // Replacing the feed with one report moves a lot of content off screen. Put
  // the cursor where the reading starts, or a screen reader is left at the top
  // of a page that no longer says what it said a moment ago.
  const heading = useRef(null);

  useEffect(() => {
    heading.current?.focus();
  }, [issueId]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    Promise.all([api.getIssue(issueId), api.listComments(issueId)])
      .then(([loadedIssue, loadedComments]) => {
        if (!active) return;
        setIssue(loadedIssue);
        setComments(loadedComments);
      })
      .catch((problem) => {
        if (active) setError(problem.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [issueId, viewerId]);

  async function castVote() {
    setVoting(true);
    try {
      const result = await api.toggleVote(issueId);
      setIssue((current) =>
        current ? { ...current, voteCount: result.voteCount, votedByMe: result.voted } : current
      );
      onChanged();
    } catch (problem) {
      pushToast(problem.message, 'bad');
    } finally {
      setVoting(false);
    }
  }

  async function toggleVote() {
    // Carry the click through the sign-in dialog, so backing a report on first
    // contact takes one round trip instead of two.
    if (!requireUser('Sign in so each report is backed once per person.', castVote)) return;
    await castVote();
  }

  /**
   * Answering the verification window.
   *
   * The server sends the whole report back rather than a tally, because this one
   * answer can settle the window - the reply may say the status is now Fixed or
   * Still not fixed. Patching a count locally would leave the badge showing
   * "Checking the fix" on a report that has just closed.
   */
  async function answerVerification(fixed) {
    setConfirming(true);
    try {
      const updated = await api.confirmFix(issueId, fixed);
      setIssue(updated);
      onChanged();
      pushToast(
        updated.status === 'VERIFYING'
          ? 'Your answer has been counted.'
          : `Settled — marked ${updated.statusLabel.toLowerCase()}.`,
        'good',
      );
    } catch (problem) {
      pushToast(problem.message, 'bad');
    } finally {
      setConfirming(false);
    }
  }

  async function sendReply() {
    setPosting(true);
    setReplyError(null);
    try {
      const created = await api.addComment(issueId, reply);
      setComments((current) => [...current, created]);
      setReply('');
      // The reply count shown here is derived from `comments`, and the feed's
      // copy is refreshed from the server by onChanged, so there is nothing
      // local to bump. A hand-incremented counter would only be a second
      // source of truth waiting to disagree with the first.
      onChanged();
    } catch (problem) {
      setReplyError(problem.message);
    } finally {
      setPosting(false);
    }
  }

  async function postReply(event) {
    event.preventDefault();
    if (!requireUser('Sign in to join the discussion.', sendReply)) return;
    await sendReply();
  }

  if (loading) {
    return (
      <section className="state">
        <p>Opening the report</p>
      </section>
    );
  }
  if (!issue) {
    return (
      <section className="state">
        <h2 className="state-head">That report is gone</h2>
        <p>{error ?? 'It may have been removed since the feed was loaded.'}</p>
        <button type="button" className="btn" onClick={onBack}>
          Back to all reports
        </button>
      </section>
    );
  }

  return (
    <>
      <article className="detail card" style={{ '--stripe': categoryColor(issue.category) }}>
        <button type="button" className="back-btn" onClick={onBack}>
          <svg width="14" height="12" viewBox="0 0 14 12" aria-hidden="true" focusable="false">
            <path
              d="M6 1 1 6l5 5M1 6h12"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.6"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
          Back to all reports
        </button>

        <h2 className="detail-title" ref={heading} tabIndex={-1}>
          {issue.title}
        </h2>
        <p className="detail-where">{issue.area}</p>

        <div className="entry-foot">
          <span className="tag" style={{ '--stripe': categoryColor(issue.category) }}>
            {issue.categoryLabel}
          </span>
          <span className="status" data-status={issue.status}>
            {issue.statusLabel}
          </span>
        </div>

        <p className="detail-body">{issue.description}</p>

        {error && (
          <p className="notice" role="alert">
            {error}
          </p>
        )}

        <ProgressPanel
          issue={issue}
          busy={confirming}
          signedIn={signedIn}
          onConfirm={answerVerification}
          onSignIn={() => requireUser('Sign in to say whether this was fixed.')}
        />

        <div className="detail-strip">
          <div className="detail-backing">
            <VoteControl
              count={issue.voteCount}
              voted={issue.votedByMe}
              busy={voting}
              onToggle={toggleVote}
              titleForLabel={issue.title}
            />
            <span className="byline">
              {issue.votedByMe ? 'You are backing this' : 'Back this if it affects you too'}
            </span>
          </div>

          {/*
            Moving a report along is no longer a dropdown here. Each transition
            now carries information a select cannot hold - a date, a note, a
            verification window - so it belongs on the dashboard, where there is
            room to ask for it. This is a signpost, not a control.
          */}
          {canModerate ? (
            <a className="btn btn-quiet" href="/admin.html">
              Open in the dashboard
            </a>
          ) : (
            <p className="byline status-note">
              {signedIn
                ? 'The ward updates this as the work moves.'
                : 'Sign in to back this report or join the discussion.'}
            </p>
          )}
        </div>

        <p className="byline">
          Reported by {issue.author} on {fullDate(issue.createdAt)}
        </p>
      </article>

      <section className="discussion card">
        <h3 className="discussion-head">
          {comments.length === 0
            ? 'No replies yet'
            : comments.length === 1
              ? '1 reply'
              : `${comments.length} replies`}
        </h3>

        {comments.length > 0 && (
          <ul className="comments">
            {comments.map((comment) => (
              <li key={comment.id} className="comment">
                <span className="avatar avatar-sm" aria-hidden="true">
                  {comment.author.slice(0, 1).toUpperCase()}
                </span>
                <div className="comment-main">
                  <div className="comment-head">
                    <span className="comment-who">{comment.author}</span>
                    <span className="comment-when">{timeAgo(comment.createdAt)}</span>
                  </div>
                  <p className="comment-body">{comment.body}</p>
                </div>
              </li>
            ))}
          </ul>
        )}

        <form className="reply-box" onSubmit={postReply} noValidate>
          {replyError && (
            <p className="notice" role="alert">
              {replyError}
            </p>
          )}

          <div className="field">
            <label htmlFor="reply">Add what you know</label>
            <span className="hint" id="reply-hint">
              Whether it is happening to you as well, who you have already contacted, what changed.
            </span>
            <textarea
              id="reply"
              className="textarea"
              value={reply}
              maxLength={2000}
              aria-describedby="reply-hint"
              placeholder={signedIn ? '' : 'Sign in to reply'}
              onChange={(event) => setReply(event.target.value)}
            />
          </div>

          <button type="submit" className="btn btn-primary" disabled={posting}>
            {posting ? 'Posting' : 'Post reply'}
          </button>
        </form>
      </section>
    </>
  );
}
