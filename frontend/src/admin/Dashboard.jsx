import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { adminApi, api } from '../api.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { useToast } from '../components/Toasts.jsx';
import { useDebounced } from '../lib/useDebounced.js';
import { applyTheme, initTheme } from '../lib/theme.js';
import SummaryStrip from './SummaryStrip.jsx';
import QueueRow from './QueueRow.jsx';
import ScheduleDialog from './ScheduleDialog.jsx';

/**
 * The tabs, and the statuses behind each one.
 *
 * The server owns the real definition - AdminController.bucketOf decides what
 * comes back. The copy here is only used to notice that a row you just actioned
 * no longer belongs on the tab you are looking at, which is a question about
 * this screen rather than about the data. A new status means touching both.
 */
const BUCKETS = [
  { key: 'inbox', label: 'Needs a date', statuses: ['REPORTED'] },
  { key: 'owed', label: 'Scheduled', statuses: ['ACKNOWLEDGED', 'IN_PROGRESS'] },
  { key: 'verifying', label: 'Being checked', statuses: ['VERIFYING'] },
  { key: 'disputed', label: 'Disputed', statuses: ['DISPUTED'] },
  { key: 'resolved', label: 'Closed', statuses: ['RESOLVED'] },
  { key: 'all', label: 'Everything', statuses: null },
];

const SORTS = [
  { key: 'eta', label: 'Promised date' },
  { key: 'deadline', label: 'Checking window closing' },
  { key: 'top', label: 'Most backed' },
  { key: 'new', label: 'Newest' },
  { key: 'oldest', label: 'Oldest' },
  { key: 'discussed', label: 'Most discussed' },
];

const PAGE = 25;

/**
 * The whole question the queue is asking, including which page of the answer.
 *
 * One object rather than a state hook each, so that narrowing the queue and
 * going back to the first page are a single update. Split across two, the
 * effect that fetches would fire once with the new filter and the old page
 * before firing again with both correct - one wasted request every time
 * somebody touches a dropdown.
 */
const START = {
  bucket: 'inbox',
  category: '',
  area: '',
  overdue: false,
  sort: 'eta',
  page: 0,
};

export default function Dashboard() {
  const { user, signOut } = useAuth();
  const pushToast = useToast();

  const [theme, setTheme] = useState(initTheme);

  const [categories, setCategories] = useState([]);
  const [areas, setAreas] = useState([]);

  const [view, setView] = useState(START);
  const [search, setSearch] = useState('');
  const settledSearch = useDebounced(search, 300);

  const [summary, setSummary] = useState(null);
  const [rows, setRows] = useState([]);
  const [meta, setMeta] = useState({ page: 0, totalItems: 0, totalPages: 0, hasMore: false });

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);

  // Which report the date dialog is for, and whether this is a first commitment
  // or a revision. Null when the dialog is closed.
  const [scheduling, setScheduling] = useState(null);

  // Rows actioned since the last load that no longer belong on this tab. They
  // are marked rather than yanked out from under the cursor - see applyChange.
  const [moved, setMoved] = useState(() => new Set());

  useEffect(() => {
    api.categories().then(setCategories).catch(() => setCategories([]));
    api.areas().then(setAreas).catch(() => setAreas([]));
  }, []);

  /** Narrowing the queue always returns to the first page of it. */
  const narrow = useCallback((patch) => {
    setView((current) => ({ ...current, ...patch, page: 0 }));
  }, []);

  const goToPage = useCallback((page) => {
    setView((current) => (current.page === page ? current : { ...current, page }));
  }, []);

  // Same ticket guard as the board: a slow earlier reply must not overwrite the
  // queue with rows for a tab the person has already left.
  const latest = useRef(0);

  useEffect(() => {
    const ticket = ++latest.current;
    let active = true;
    setLoading(true);
    setError(null);

    Promise.all([
      adminApi.listIssues({
        bucket: view.bucket === 'all' ? '' : view.bucket,
        category: view.category,
        area: view.area,
        search: settledSearch,
        overdue: view.overdue,
        sort: view.sort,
        page: view.page,
        size: PAGE,
      }),
      // The counters describe the whole board rather than this tab, so a failure
      // here should not take the queue down with it.
      adminApi.summary().catch(() => null),
    ])
      .then(([pageData, freshSummary]) => {
        if (!active || ticket !== latest.current) return;
        setRows(pageData.items);
        setMeta({
          page: pageData.page,
          totalItems: pageData.totalItems,
          totalPages: pageData.totalPages,
          hasMore: pageData.hasMore,
        });
        if (freshSummary) setSummary(freshSummary);
        setMoved(new Set());
      })
      .catch((problem) => {
        if (active && ticket === latest.current) setError(problem.message);
      })
      .finally(() => {
        if (active && ticket === latest.current) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [view, settledSearch]);

  function toggleTheme() {
    const next = theme === 'dark' ? 'light' : 'dark';
    setTheme(next);
    applyTheme(next);
  }

  const belongsHere = useCallback(
    (status) => {
      const here = BUCKETS.find((entry) => entry.key === view.bucket);
      return !here?.statuses || here.statuses.includes(status);
    },
    [view.bucket]
  );

  /**
   * Writes a transition's answer back into the queue.
   *
   * Every transition returns the whole report, so the row is replaced rather
   * than patched - the server may have changed more than the status, and a
   * hand-merged row is a second opinion waiting to disagree. The counters are
   * re-read because a transition always moves at least one of them.
   */
  function applyChange(updated) {
    setRows((current) => current.map((row) => (row.id === updated.id ? updated : row)));
    if (!belongsHere(updated.status)) {
      setMoved((current) => new Set(current).add(updated.id));
    }
    adminApi.summary().then(setSummary).catch(() => {});
  }

  async function run(id, action, done) {
    setBusyId(id);
    try {
      applyChange(await action());
      pushToast(done, 'good');
    } catch (problem) {
      pushToast(problem.message, 'bad');
    } finally {
      setBusyId(null);
    }
  }

  /**
   * Throws on failure rather than reporting it, so the dialog can keep the
   * message next to the field that caused it. Only a success reaches the last
   * three lines, which is why the dialog closes here and not in a finally.
   */
  async function saveSchedule({ etaDate, note }) {
    const { id, mode } = scheduling;
    const updated =
      mode === 'acknowledge'
        ? await adminApi.acknowledge(id, { etaDate, note })
        : await adminApi.schedule(id, { etaDate, note });

    applyChange(updated);
    setScheduling(null);
    pushToast(
      mode === 'acknowledge' ? 'Picked up, and the date is now public.' : 'New date published.',
      'good'
    );
  }

  const filtering =
    Boolean(view.category) || Boolean(view.area) || view.overdue || Boolean(settledSearch);

  function clearFilters() {
    setSearch('');
    narrow({ category: '', area: '', overdue: false });
  }

  const counts = useMemo(() => countsByStatus(summary), [summary]);

  return (
    <div className="adm">
      <header className="adm-top">
        <div className="adm-top-inner">
          <a className="adm-brand" href="/">
            <span className="brand-mark" aria-hidden="true">
              <svg width="16" height="16" viewBox="0 0 18 18" focusable="false">
                <path
                  d="M9 1.5c3.3 0 6 2.6 6 5.8 0 4-6 9.2-6 9.2S3 11.3 3 7.3c0-3.2 2.7-5.8 6-5.8Z"
                  fill="currentColor"
                />
                <circle cx="9" cy="7.2" r="2.2" fill="var(--brand-mark-dot)" />
              </svg>
            </span>
            <span className="adm-brand-name">Nagarik</span>
            <span className="adm-brand-sep" aria-hidden="true" />
            <span className="adm-brand-what">Ward dashboard</span>
          </a>

          <div className="adm-top-actions">
            <button
              type="button"
              className="icon-btn"
              onClick={toggleTheme}
              aria-label={theme === 'dark' ? 'Switch to the light theme' : 'Switch to the dark theme'}
            >
              {theme === 'dark' ? (
                <svg width="16" height="16" viewBox="0 0 17 17" aria-hidden="true" focusable="false">
                  <circle cx="8.5" cy="8.5" r="3.4" fill="currentColor" />
                  <g stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                    <path d="M8.5 .8v2M8.5 14.2v2M.8 8.5h2M14.2 8.5h2M3 3l1.4 1.4M12.6 12.6 14 14M14 3l-1.4 1.4M4.4 12.6 3 14" />
                  </g>
                </svg>
              ) : (
                <svg width="16" height="16" viewBox="0 0 17 17" aria-hidden="true" focusable="false">
                  <path
                    d="M14.5 10.4A6.4 6.4 0 0 1 6.6 2.5a6.5 6.5 0 1 0 7.9 7.9Z"
                    fill="currentColor"
                  />
                </svg>
              )}
            </button>

            <a className="btn btn-quiet" href="/">
              The board
            </a>

            <span className="adm-who">
              <span className="avatar avatar-sm" aria-hidden="true">
                {user.displayName.slice(0, 1).toUpperCase()}
              </span>
              <span className="adm-who-name">{user.displayName}</span>
            </span>

            <button type="button" className="btn btn-quiet" onClick={signOut}>
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main className="adm-body">
        <SummaryStrip
          summary={summary}
          bucket={view.bucket}
          overdueOn={view.overdue}
          onShowBucket={(key) => narrow({ bucket: key, overdue: false })}
          onShowOverdue={() => narrow({ bucket: 'all', overdue: !view.overdue })}
        />

        <nav className="adm-tabs" aria-label="Queues">
          {BUCKETS.map((entry) => (
            <button
              key={entry.key}
              type="button"
              className={`adm-tab${view.bucket === entry.key ? ' is-on' : ''}`}
              aria-current={view.bucket === entry.key ? 'page' : undefined}
              onClick={() => narrow({ bucket: entry.key })}
            >
              {entry.label}
              <span className="adm-tab-count">{tabCount(entry, counts, summary)}</span>
            </button>
          ))}
        </nav>

        <div className="adm-controls">
          <input
            className="input adm-search"
            type="search"
            value={search}
            placeholder="Search titles, descriptions and localities"
            aria-label="Search the queue"
            onChange={(event) => {
              setSearch(event.target.value);
              // The fetch waits for the typing to settle; the page number does
              // not need to. Resetting on the keystroke means the settled value
              // is never asked for at page four of the previous search.
              goToPage(0);
            }}
          />

          <select
            className="select"
            value={view.category}
            aria-label="Filter by category"
            onChange={(event) => narrow({ category: event.target.value })}
          >
            <option value="">Every category</option>
            {categories.map((entry) => (
              <option key={entry.value} value={entry.value}>
                {entry.label}
              </option>
            ))}
          </select>

          <select
            className="select"
            value={view.area}
            aria-label="Filter by locality"
            onChange={(event) => narrow({ area: event.target.value })}
          >
            <option value="">Every locality</option>
            {areas.map((area) => (
              <option key={area} value={area}>
                {area}
              </option>
            ))}
          </select>

          <select
            className="select"
            value={view.sort}
            aria-label="Order the queue"
            onChange={(event) => narrow({ sort: event.target.value })}
          >
            {SORTS.map((entry) => (
              <option key={entry.key} value={entry.key}>
                {entry.label}
              </option>
            ))}
          </select>

          <label className="adm-check">
            <input
              type="checkbox"
              checked={view.overdue}
              onChange={(event) => narrow({ overdue: event.target.checked })}
            />
            Past its date only
          </label>

          {filtering && (
            <button type="button" className="btn btn-quiet" onClick={clearFilters}>
              Clear
            </button>
          )}
        </div>

        {error && (
          <p className="notice" role="alert">
            {error}
          </p>
        )}

        <div className="adm-count">
          {loading
            ? 'Reading the queue'
            : meta.totalItems === 0
              ? 'Nothing in this queue'
              : `${meta.totalItems} ${meta.totalItems === 1 ? 'report' : 'reports'}${
                  meta.totalPages > 1 ? ` · page ${meta.page + 1} of ${meta.totalPages}` : ''
                }`}
        </div>

        {loading ? (
          <div className="adm-table adm-table-loading" aria-hidden="true">
            {Array.from({ length: 6 }, (_, index) => (
              <div className="adm-skeleton" key={index} />
            ))}
          </div>
        ) : rows.length === 0 ? (
          <section className="state">
            <h2 className="state-head">{filtering ? 'Nothing matches that' : 'Queue is clear'}</h2>
            <p>
              {filtering
                ? 'No reports in this queue match what you are asking for.'
                : 'Nothing is waiting on you here. The other tabs may have work.'}
            </p>
            {filtering && (
              <div className="btn-row btn-row-center">
                <button type="button" className="btn btn-quiet" onClick={clearFilters}>
                  Clear filters
                </button>
              </div>
            )}
          </section>
        ) : (
          <div className="adm-table">
            <div className="adm-head" aria-hidden="true">
              <span>Report</span>
              <span>Backing</span>
              <span>Promised</span>
              <span>Checking</span>
              <span>What now</span>
            </div>

            {rows.map((issue) => (
              <QueueRow
                key={issue.id}
                issue={issue}
                busy={busyId === issue.id}
                moved={moved.has(issue.id)}
                onSchedule={(mode) => setScheduling({ id: issue.id, mode, issue })}
                onStart={() =>
                  run(issue.id, () => adminApi.start(issue.id), 'Marked as work in progress.')
                }
                onDone={() =>
                  run(
                    issue.id,
                    () => adminApi.markDone(issue.id),
                    'Handed back to the people who backed it. They have a week to check it.'
                  )
                }
                onReopen={() =>
                  run(issue.id, () => adminApi.reopen(issue.id), 'Back on the pile as in progress.')
                }
              />
            ))}
          </div>
        )}

        {meta.totalPages > 1 && (
          <div className="adm-pager">
            <button
              type="button"
              className="btn btn-quiet"
              disabled={meta.page === 0 || loading}
              onClick={() => goToPage(Math.max(view.page - 1, 0))}
            >
              Previous
            </button>
            <span className="adm-pager-at">
              Page {meta.page + 1} of {meta.totalPages}
            </span>
            <button
              type="button"
              className="btn btn-quiet"
              disabled={!meta.hasMore || loading}
              onClick={() => goToPage(view.page + 1)}
            >
              Next
            </button>
          </div>
        )}
      </main>

      {scheduling && (
        <ScheduleDialog
          issue={scheduling.issue}
          mode={scheduling.mode}
          onSave={saveSchedule}
          onCancel={() => setScheduling(null)}
        />
      )}
    </div>
  );
}

/** byStatus as a lookup, so the tabs can show exact numbers rather than sums. */
function countsByStatus(summary) {
  const map = {};
  (summary?.byStatus ?? []).forEach((entry) => {
    map[entry.value] = entry.total;
  });
  return map;
}

function tabCount(entry, counts, summary) {
  if (!summary) return '';
  if (!entry.statuses) return summary.total;
  return entry.statuses.reduce((total, status) => total + (counts[status] ?? 0), 0);
}
