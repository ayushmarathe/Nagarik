/**
 * Placeholder rows shaped like the real ones, so the feed does not jump when
 * the answer arrives. Hidden from assistive tech - the live region next to it
 * carries the fact that something is loading.
 */
export default function Skeleton({ rows = 6 }) {
  const widths = ['68%', '44%', '84%'];

  return (
    <div className="skeleton" aria-hidden="true">
      {Array.from({ length: rows }, (_, row) => (
        <div className="skeleton-row" key={row} style={{ '--i': row }}>
          <div className="skeleton-num" />
          <div className="skeleton-lines">
            {widths.map((width, line) => (
              <div className="skeleton-bar" key={line} style={{ width }} />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
