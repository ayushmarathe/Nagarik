import VoteControl from './VoteControl.jsx';
import { categoryColor } from '../lib/categories.js';
import { dueLabel, shortDate, timeAgo } from '../lib/format.js';

/** Long descriptions get trimmed to a couple of lines in the feed. */
function excerpt(text) {
  if (!text) return '';
  const flat = text.replace(/\s+/g, ' ').trim();
  return flat.length > 190 ? `${flat.slice(0, 187).trimEnd()}...` : flat;
}

export default function IssueRow({ issue, index = 0, busy, onToggleVote, onOpen }) {
  const promised = issue.etaDate && ['ACKNOWLEDGED', 'IN_PROGRESS', 'DISPUTED'].includes(issue.status);

  return (
    <article
      className="entry card"
      style={{
        '--stripe': categoryColor(issue.category),
        // Rows fade in one after another rather than all at once. Capped, so a
        // long page does not end with rows arriving a second and a half late.
        '--delay': `${Math.min(index, 8) * 40}ms`,
      }}
    >
      <VoteControl
        count={issue.voteCount}
        voted={issue.votedByMe}
        busy={busy}
        onToggle={() => onToggleVote(issue)}
        titleForLabel={issue.title}
      />

      <div className="entry-main">
        <h3 className="entry-title">
          <button type="button" className="title-btn" onClick={() => onOpen(issue.id)}>
            {issue.title}
          </button>
        </h3>

        <p className="entry-excerpt">{excerpt(issue.description)}</p>

        <div className="entry-foot">
          <span className="tag" style={{ '--stripe': categoryColor(issue.category) }}>
            {issue.categoryLabel}
          </span>
          <span className="status" data-status={issue.status}>
            {issue.statusLabel}
          </span>

          {/*
            One chip, and only when there is something to say. The feed is
            scanned rather than read, so a row that repeats the same neutral
            metadata on every card teaches people to skip the whole strip.
          */}
          {promised && (
            <span className="chip" data-late={issue.overdue ? 'yes' : 'no'}>
              {issue.overdue ? dueLabel(issue.etaDate) : `by ${shortDate(issue.etaDate)}`}
            </span>
          )}

          <span className="entry-where">{issue.area}</span>
          <span className="entry-replies">
            {issue.commentCount === 1 ? '1 reply' : `${issue.commentCount} replies`}
          </span>
          <span className="entry-when">{timeAgo(issue.createdAt)}</span>
        </div>

        {/*
          The one row in the feed that is asking the reader for something. It
          only appears for people entitled to answer, so it stays rare enough to
          still read as a request rather than decoration.
        */}
        {issue.canVerify && (
          <button type="button" className="entry-ask" onClick={() => onOpen(issue.id)}>
            <span className="entry-ask-dot" aria-hidden="true" />
            The ward says this is done — you backed it, so does it look fixed?
          </button>
        )}
      </div>
    </article>
  );
}
