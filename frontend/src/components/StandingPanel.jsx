import { categoryColor } from '../lib/categories.js';

/**
 * How the neighbourhood is standing overall, and what it is standing in. The
 * bars double as filters, so the summary is also a way into the feed.
 */
export default function StandingPanel({ stats, onPickCategory }) {
  if (!stats) {
    return (
      <aside className="standing card" aria-label="Standing">
        <h2 className="standing-head">Standing</h2>
        <p className="standing-empty">Counting what is reported</p>
      </aside>
    );
  }

  const busiest = Math.max(1, ...(stats.byCategory ?? []).map((row) => row.total));
  const fixedShare = stats.total === 0 ? 0 : Math.round((stats.resolved / stats.total) * 100);

  return (
    <aside className="standing card" aria-label="Standing">
      <h2 className="standing-head">Standing</h2>

      <div className="standing-hero">
        <span className="standing-big-num">{stats.total}</span>
        <span className="standing-big-word">
          {stats.total === 1 ? 'report' : 'reports'} on the board
        </span>
      </div>

      <div className="progress" role="img" aria-label={`${fixedShare}% of reports fixed`}>
        <span className="progress-fill" style={{ width: `${fixedShare}%` }} />
      </div>
      <p className="progress-note">{fixedShare}% fixed so far</p>

      <dl className="standing-grid">
        <div className="standing-cell">
          <dt>Open</dt>
          <dd>{stats.open}</dd>
        </div>
        <div className="standing-cell">
          <dt>Fixed</dt>
          <dd>{stats.resolved}</dd>
        </div>
        <div className="standing-cell">
          <dt>Backings</dt>
          <dd>{stats.backings}</dd>
        </div>
        <div className="standing-cell">
          <dt>Residents</dt>
          <dd>{stats.residents}</dd>
        </div>
      </dl>

      {(stats.byCategory ?? []).length > 0 && (
        <div className="standing-breakdown">
          <h3 className="standing-sub">Where it is concentrated</h3>
          <ul className="bars">
            {stats.byCategory.map((row) => (
              <li key={row.value}>
                <button
                  type="button"
                  className="bar-row"
                  style={{ '--stripe': categoryColor(row.value) }}
                  onClick={() => onPickCategory(row.value)}
                >
                  <span className="bar-label">{row.label}</span>
                  <span className="bar-track">
                    <span
                      className="bar-fill"
                      style={{ width: `${Math.round((row.total / busiest) * 100)}%` }}
                    />
                  </span>
                  <span className="bar-count">{row.total}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </aside>
  );
}
