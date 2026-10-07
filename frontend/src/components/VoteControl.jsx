/**
 * The number is the point of the whole app, so it is set large and in the one
 * loud colour the palette keeps in reserve.
 *
 * The chevron sits above it rather than below, which is where every board people
 * already use puts it. Being unfamiliar here would cost more than it gained.
 */
export default function VoteControl({ count, voted, busy, onToggle, titleForLabel }) {
  return (
    <div className="backing">
      <button
        type="button"
        className="vote"
        aria-pressed={voted}
        disabled={busy}
        onClick={onToggle}
        aria-label={
          voted
            ? `Remove your backing from ${titleForLabel}`
            : `Back this report: ${titleForLabel}`
        }
      >
        <svg width="15" height="10" viewBox="0 0 15 10" aria-hidden="true" focusable="false">
          <path
            d="M1.5 8.5 7.5 2l6 6.5"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </button>
      <span className="backing-count">{count}</span>
      <span className="backing-word">{count === 1 ? 'person' : 'people'}</span>
    </div>
  );
}
