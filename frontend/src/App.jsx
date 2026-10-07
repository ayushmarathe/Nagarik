import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api } from './api.js';
import { useDebounced } from './lib/useDebounced.js';
import { applyTheme, initTheme } from './lib/theme.js';
import { AuthProvider, useAuth } from './auth/AuthContext.jsx';
import { ToastProvider, useToast } from './components/Toasts.jsx';
import Header from './components/Header.jsx';
import FilterBar from './components/FilterBar.jsx';
import StandingPanel from './components/StandingPanel.jsx';
import SearchBox from './components/SearchBox.jsx';
import IssueRow from './components/IssueRow.jsx';
import IssueDetail from './components/IssueDetail.jsx';
import ReportForm from './components/ReportForm.jsx';
import AuthDialog from './components/AuthDialog.jsx';
import AccountDialog from './components/AccountDialog.jsx';
import Skeleton from './components/Skeleton.jsx';

const NO_FILTERS = { category: null, status: null, area: null, sort: 'top' };

/**
 * Paging appends to what is already on screen. A row can only appear twice if
 * the underlying order shifted between two requests, which the server avoids
 * by tie-breaking every sort on id - this is the cheap second line of defence.
 */
function mergePages(current, incoming) {
  const seen = new Set(current.map((row) => row.id));
  return [...current, ...incoming.filter((row) => !seen.has(row.id))];
}

/**
 * ?issue=12 opens that report straight away.
 *
 * Read once, on load, and never written back - the board is otherwise a single
 * screen with no history to keep in step, and a full router would be a lot of
 * machinery for one link. What it buys is the dashboard being able to send a
 * moderator to the discussion behind a report, which is where residents put
 * most of what is worth knowing about it.
 */
function readOpenId() {
  const raw = new URLSearchParams(window.location.search).get('issue');
  const id = Number(raw);
  return Number.isInteger(id) && id > 0 ? id : null;
}

/**
 * Providers, then the board. The order matters: the session reports notices
 * through the toaster, so it has to sit inside it.
 */
export default function App() {
  return (
    <ToastProvider>
      <Board />
    </ToastProvider>
  );
}

/**
 * The whole screen. Sits inside the toaster so it can raise a notice, and
 * provides the session to everything below it - the header needs to know who is
 * signed in, and the feed needs to know whether to ask for the viewer's votes.
 */
function Board() {
  const pushToast = useToast();
  return (
    <AuthProvider onNotice={pushToast}>
      <Shell />
    </AuthProvider>
  );
}

function Shell() {
  const pushToast = useToast();
  const { user, signedIn, requireUser, prompt } = useAuth();

  const [theme, setTheme] = useState(initTheme);

  const [categories, setCategories] = useState([]);
  const [statuses, setStatuses] = useState([]);
  const [areas, setAreas] = useState([]);

  const [filters, setFilters] = useState(NO_FILTERS);
  const [search, setSearch] = useState('');
  const settledSearch = useDebounced(search, 300);

  const [issues, setIssues] = useState([]);
  const [meta, setMeta] = useState({ page: 0, totalItems: 0, hasMore: false });
  const [stats, setStats] = useState(null);

  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(null);

  const [openId, setOpenId] = useState(readOpenId);
  const [reporting, setReporting] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [votingId, setVotingId] = useState(null);
  const [railOpen, setRailOpen] = useState(false);

  // The feed only cares whether somebody is signed in, not who - the server
  // answers votedByMe from the token. Depending on the id rather than the user
  // object keeps a rename from looking like a different person and reloading
  // everything.
  const viewerId = user?.id ?? null;

  // Filter lists, pickers and the theme.
  useEffect(() => {
    api.categories().then(setCategories).catch(() => setCategories([]));
    api.statuses().then(setStatuses).catch(() => setStatuses([]));
    api.areas().then(setAreas).catch(() => setAreas([]));
  }, []);

  function toggleTheme() {
    const next = theme === 'dark' ? 'light' : 'dark';
    setTheme(next);
    applyTheme(next);
  }

  // Recomputed only when something the server cares about actually changes,
  // which is what lets loadFeed stay stable for the effect below.
  const feedQuery = useMemo(
    () => ({ ...filters, search: settledSearch }),
    [filters, settledSearch]
  );

  // Two loads can be in the air at once: a search landing on top of the first
  // load, or React's development double-render. Only the newest is allowed to
  // write, otherwise a slow earlier reply overwrites the feed with rows for a
  // query the person has already moved off.
  const latestLoad = useRef(0);

  const loadFeed = useCallback(
    async (page) => {
      const ticket = ++latestLoad.current;
      if (page === 0) setLoading(true);
      else setLoadingMore(true);
      setError(null);

      try {
        const [pageData, freshStats] = await Promise.all([
          api.listIssues({ ...feedQuery, page }),
          // Counts describe the whole board, not the filtered view, so they
          // only need fetching when the page is rebuilt from the top.
          page === 0 ? api.stats().catch(() => null) : Promise.resolve(null),
        ]);

        if (ticket !== latestLoad.current) return;

        setIssues((current) =>
          page === 0 ? pageData.items : mergePages(current, pageData.items)
        );
        setMeta({
          page: pageData.page,
          totalItems: pageData.totalItems,
          hasMore: pageData.hasMore,
        });
        if (freshStats) setStats(freshStats);
      } catch (problem) {
        if (ticket === latestLoad.current) setError(problem.message);
      } finally {
        if (ticket === latestLoad.current) {
          setLoading(false);
          setLoadingMore(false);
        }
      }
    },
    [feedQuery]
  );

  // Reloads when the query changes and when somebody signs in or out, since the
  // backing state on every row depends on who is asking.
  useEffect(() => {
    loadFeed(0);
  }, [loadFeed, viewerId]);

  // Opening a report should start you at the top of it.
  useEffect(() => {
    if (openId !== null) window.scrollTo({ top: 0, behavior: 'smooth' });
  }, [openId]);

  // While a sheet or dialog is up, the page behind it should not scroll.
  //
  // The sign-in dialog counts. It renders its own overlay from the auth
  // context rather than from state held here, so it has to be read from
  // "prompt" - leaving it out let the board scroll behind the one dialog a
  // first-time visitor is most likely to see.
  const overlayOpen = reporting || accountOpen || prompt !== null;
  useEffect(() => {
    if (!overlayOpen) return undefined;
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previous;
    };
  }, [overlayOpen]);

  useEffect(() => {
    if (!reporting) return undefined;
    function onKeyDown(event) {
      if (event.key === 'Escape') setReporting(false);
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [reporting]);

  function openReportForm() {
    setOpenId(null);
    setReporting(true);
  }

  function startReport() {
    if (!requireUser('Sign in to post a report.', openReportForm)) return;
    openReportForm();
  }

  async function createIssue(values) {
    // The form only opens for somebody signed in, so this is a guard rather
    // than a prompt. Throwing keeps the message inside the form.
    if (!signedIn) throw new Error('Sign in before posting a report.');

    const created = await api.createIssue(values);
    setReporting(false);
    pushToast('Your report is on the board.', 'good');
    // A new locality should appear in the picker straight away.
    api.areas().then(setAreas).catch(() => {});
    await loadFeed(0);
    // A brand new report has no backing yet, so it would land at the bottom of
    // the feed. Open it instead, so posting visibly leads somewhere.
    setOpenId(created.id);
  }

  async function castVote(issue) {
    setVotingId(issue.id);
    try {
      const result = await api.toggleVote(issue.id);
      setIssues((current) =>
        current.map((row) =>
          row.id === issue.id
            ? { ...row, voteCount: result.voteCount, votedByMe: result.voted }
            : row
        )
      );
      api.stats().then(setStats).catch(() => {});
    } catch (problem) {
      pushToast(problem.message, 'bad');
    } finally {
      setVotingId(null);
    }
  }

  async function toggleVote(issue) {
    // Carry the click through the sign-in dialog, so backing a report on first
    // contact takes one round trip instead of two.
    if (!requireUser('Sign in so each report is backed once per person.', () => castVote(issue))) {
      return;
    }
    await castVote(issue);
  }

  /**
   * Re-reads one report after it changed on the detail screen. Refetching the
   * whole feed would throw away every extra page the person had loaded, so
   * only the row that moved is replaced.
   */
  async function syncIssue(id) {
    try {
      const fresh = await api.getIssue(id);
      setIssues((current) => current.map((row) => (row.id === id ? fresh : row)));
    } catch {
      // Leave the row as it was; the detail screen already reported the change.
    }
    api.stats().then(setStats).catch(() => {});
  }

  /**
   * FilterBar hands back a spread of the filter object, which includes the
   * search term it was given for display. Search is settled separately, so it
   * is dropped here rather than allowed to sit in this state as a stale copy.
   */
  function changeFilters(next) {
    const wanted = {
      category: next.category ?? null,
      status: next.status ?? null,
      area: next.area ?? null,
      sort: next.sort ?? 'top',
    };

    // loadFeed depends on this object, so keep the old one when nothing
    // actually changed and re-selecting the active filter does not reload.
    setFilters((current) =>
      current.category === wanted.category &&
      current.status === wanted.status &&
      current.area === wanted.area &&
      current.sort === wanted.sort
        ? current
        : wanted
    );
  }

  function clearFilters() {
    setSearch('');
    changeFilters(NO_FILTERS);
  }

  function pickCategory(category) {
    changeFilters({ ...filters, category: filters.category === category ? null : category });
  }

  const filtering =
    Boolean(filters.category) ||
    Boolean(filters.status) ||
    Boolean(filters.area) ||
    Boolean(settledSearch);

  return (
    <div className="app">
      <Header
        stats={stats}
        theme={theme}
        onToggleTheme={toggleTheme}
        onReport={startReport}
        onOpenAccount={() => setAccountOpen(true)}
      />

      <AuthDialog />
      <AccountDialog open={accountOpen} onClose={() => setAccountOpen(false)} />

      <div className="board">
        <div className={`rail${railOpen ? ' is-open' : ''}`}>
          <FilterBar
            categories={categories}
            statuses={statuses}
            areas={areas}
            stats={stats}
            filters={{ ...filters, search: settledSearch }}
            onChange={changeFilters}
            onClear={clearFilters}
          />
          <StandingPanel stats={stats} onPickCategory={pickCategory} />
        </div>

        <main className="feed-pane" id="board-top">
          {openId !== null ? (
            <IssueDetail
              issueId={openId}
              onBack={() => setOpenId(null)}
              onChanged={() => syncIssue(openId)}
            />
          ) : (
            <>
              <div className="feed-bar">
                <SearchBox
                  value={search}
                  onChange={setSearch}
                  busy={search !== settledSearch || loading}
                />

                <button
                  type="button"
                  className="btn btn-quiet rail-toggle"
                  aria-expanded={railOpen}
                  onClick={() => setRailOpen((open) => !open)}
                >
                  {railOpen ? 'Hide filters' : 'Filters'}
                  {filtering && <span className="rail-toggle-dot" aria-hidden="true" />}
                </button>
              </div>

              <div className="feed-count">
                {loading ? (
                  <span>Reading the board</span>
                ) : (
                  <span>
                    {meta.totalItems === 0
                      ? 'Nothing on the board'
                      : `Showing ${issues.length} of ${meta.totalItems}`}
                    {filtering ? ' matching' : ''}
                  </span>
                )}
              </div>

              {error && (
                <p className="notice" role="alert">
                  {error}
                </p>
              )}

              {loading ? (
                <Skeleton rows={6} />
              ) : issues.length === 0 ? (
                <section className="state">
                  <h2 className="state-head">
                    {filtering ? 'Nothing matches that' : 'Nothing here yet'}
                  </h2>
                  <p>
                    {filtering
                      ? 'No reports match what you are asking for. Widen it, or raise the first one yourself.'
                      : 'No problems have been reported yet. Raise the first one.'}
                  </p>
                  <div className="btn-row btn-row-center">
                    {filtering && (
                      <button type="button" className="btn btn-quiet" onClick={clearFilters}>
                        Clear filters
                      </button>
                    )}
                    <button type="button" className="btn btn-primary" onClick={startReport}>
                      Report a problem
                    </button>
                  </div>
                </section>
              ) : (
                <>
                  <div className="feed">
                    {issues.map((issue, index) => (
                      <IssueRow
                        key={issue.id}
                        issue={issue}
                        index={index}
                        busy={votingId === issue.id}
                        onToggleVote={toggleVote}
                        onOpen={setOpenId}
                      />
                    ))}
                  </div>

                  {meta.hasMore && (
                    <div className="more">
                      <button
                        type="button"
                        className="btn btn-wide"
                        disabled={loadingMore}
                        onClick={() => loadFeed(meta.page + 1)}
                      >
                        {loadingMore
                          ? 'Loading more reports'
                          : `Load more reports (${meta.totalItems - issues.length} left)`}
                      </button>
                    </div>
                  )}
                </>
              )}
            </>
          )}
        </main>
      </div>

      {reporting && (
        <div className="overlay" role="presentation" onClick={() => setReporting(false)}>
          <div
            className="dialog dialog-wide"
            role="dialog"
            aria-modal="true"
            aria-label="Report a problem"
            onClick={(event) => event.stopPropagation()}
          >
            <ReportForm
              categories={categories}
              onSubmit={createIssue}
              onCancel={() => setReporting(false)}
            />
          </div>
        </div>
      )}
    </div>
  );
}
