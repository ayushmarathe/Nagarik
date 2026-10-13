/**
 * Relative on purpose.
 *
 * In development Vite proxies /api to Spring Boot on port 8080, so the browser
 * only ever talks to one origin and no CORS preflight happens. In production the
 * built files and the API sit behind the same host, so the same path keeps
 * working. Hardcoding an absolute backend URL here is what breaks that: it ties
 * the bundle to one deployment and means every local run talks to the remote
 * database.
 */
const BASE = '/api';

const TOKEN_KEY = 'nagarik.token';

/**
 * The token lives in module state as well as in storage.
 *
 * In state so every request reads the same current value without touching
 * localStorage; in storage so a reload does not sign you out. localStorage is
 * readable by any script on the origin, which is the usual objection to keeping
 * a token there - the alternative that avoids it is an httpOnly cookie, which
 * brings its own CSRF problem and needs the server to set and clear it.
 */
let token = readStoredToken();

function readStoredToken() {
  try {
    return window.localStorage.getItem(TOKEN_KEY);
  } catch {
    // Private browsing blocks storage. The session still works for as long as
    // the tab is open, it just will not survive a reload.
    return null;
  }
}

export function setToken(next) {
  token = next ?? null;
  try {
    if (token) window.localStorage.setItem(TOKEN_KEY, token);
    else window.localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Storage unavailable; the in-memory copy above still signs this session.
  }
}

export function getToken() {
  return token;
}

/**
 * Called when the server rejects a token we sent - it expired, or the account
 * behind it was deleted. The session layer listens and clears the signed-in
 * person, so the header stops claiming somebody is signed in when the server no
 * longer agrees. Without this, the interface keeps showing a name that every
 * action behind it is refused for.
 */
let onSessionLost = () => {};

export function setSessionLostHandler(handler) {
  onSessionLost = handler ?? (() => {});
}

/**
 * Every call goes through here so error handling is identical everywhere.
 * The server always answers failures with a "message" written for a person,
 * and validation failures add a "fields" map, which forms read to mark inputs.
 */
async function request(path, { method = 'GET', body } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  // Read the module value at call time rather than capturing it, so a request
  // fired from a closure built before sign-in still carries the new token.
  const sent = token;
  if (sent) headers.Authorization = `Bearer ${sent}`;

  let response;
  try {
    response = await fetch(BASE + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw Object.assign(new Error('Cannot reach the server. Is the backend running on port 8080?'), {
      status: 0,
    });
  }

  if (response.status === 401 && sent) {
    // A token was sent and refused, so it is spent. A 401 without one is just a
    // failed sign-in, and must not tear down a session that was never there.
    setToken(null);
    onSessionLost();
  }

  if (!response.ok) {
    let message = 'Something went wrong. Try again.';
    let fields = null;
    try {
      const data = await response.json();
      if (data.message) message = data.message;
      if (data.fields) fields = data.fields;
    } catch {
      // response had no JSON body; the default message stands
    }
    throw Object.assign(new Error(message), { status: response.status, fields });
  }

  if (response.status === 204) return null;
  return response.json();
}

/**
 * Uploads one file as multipart/form-data.
 *
 * Separate from request() for one specific reason: the Content-Type of a
 * multipart body has to carry the boundary the browser generated, so the header
 * must be left unset and allowed to be filled in. Setting it by hand - which
 * request() does for every JSON call - produces a body the server cannot parse,
 * and the failure looks like a server bug rather than a client one.
 */
async function upload(path, file) {
  const form = new FormData();
  form.append('file', file);

  const headers = {};
  const sent = token;
  if (sent) headers.Authorization = `Bearer ${sent}`;

  let response;
  try {
    response = await fetch(BASE + path, { method: 'POST', headers, body: form });
  } catch {
    throw Object.assign(new Error('Cannot reach the server. Is the backend running?'), {
      status: 0,
    });
  }

  if (response.status === 401 && sent) {
    setToken(null);
    onSessionLost();
  }

  if (!response.ok) {
    let message = 'That file could not be uploaded.';
    try {
      const data = await response.json();
      if (data.message) message = data.message;
    } catch {
      // No JSON body - a 413 from a proxy, for instance. Default stands.
    }
    throw Object.assign(new Error(message), { status: response.status });
  }

  return response.json();
}

/**
 * Turns the server-relative media path on an attachment into something the
 * browser can load.
 *
 * The server sends "/api/attachments/12/file" - correct on its own origin, and
 * wrong here, because BASE points at a different host. Joining it onto the API
 * origin rather than the page origin is what makes the gallery work both in
 * local development and against the deployed backend.
 */
export function mediaUrl(path) {
  if (!path) return '';
  if (/^https?:\/\//i.test(path)) return path;
  const origin = BASE.replace(/\/api\/?$/, '');
  return origin + path;
}

function query(params) {  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') search.set(key, value);
  });
  const string = search.toString();
  return string ? `?${string}` : '';
}

/** How many reports one screenful asks for. The server caps this at 100. */
export const PAGE_SIZE = 12;

export const api = {
  register: (values) => request('/auth/register', { method: 'POST', body: values }),

  login: (values) => request('/auth/login', { method: 'POST', body: values }),

  /** Who the stored token belongs to. Answers 401 once it stops being valid. */
  me: () => request('/auth/me'),

  rename: (displayName) => request('/auth/me', { method: 'PATCH', body: { displayName } }),

  categories: () => request('/categories'),

  statuses: () => request('/statuses'),

  stats: () => request('/issues/stats'),

  /** Localities that exist, for the locality picker. */
  areas: () => request('/issues/areas'),

  /**
   * Answers a page of the feed, wrapped as
   * { items, page, size, totalItems, totalPages, hasMore } rather than a bare
   * array - the feed needs to know whether there is another page to ask for.
   */
  listIssues: ({ category, status, area, search, sort, page = 0, size = PAGE_SIZE }) =>
    request(`/issues${query({ category, status, area, q: search, sort, page, size })}`),

  getIssue: (id) => request(`/issues/${id}`),

  createIssue: (issue) => request('/issues', { method: 'POST', body: issue }),

  toggleVote: (id) => request(`/issues/${id}/vote`, { method: 'POST' }),

  /**
   * A backer saying whether the repair happened. Refused with a 403 for anyone
   * who has not backed the report, which the interface avoids reaching by only
   * showing the control when canVerify comes back true.
   */
  confirmFix: (id, fixed) => request(`/issues/${id}/confirm`, { method: 'POST', body: { fixed } }),

  listComments: (issueId) => request(`/issues/${issueId}/comments`),

  addComment: (issueId, body) =>
    request(`/issues/${issueId}/comments`, { method: 'POST', body: { body } }),

  /**
   * Photos and video on a report. Public, like the report itself - an anonymous
   * reader gets the gallery with canDelete false on every entry.
   */
  listAttachments: (issueId) => request(`/issues/${issueId}/attachments`),

  /** One file at a time, so a part-failed batch still leaves what succeeded. */
  addAttachment: (issueId, file) => upload(`/issues/${issueId}/attachments`, file),

  removeAttachment: (id) => request(`/attachments/${id}`, { method: 'DELETE' }),
};

/**
 * The moderator's surface. Separate object rather than more keys on `api`
 * because every call here answers 403 without a moderator account, and keeping
 * them apart makes that impossible to forget at the call site.
 *
 * There is no generic "set the status to X" call, by design: the server only
 * accepts the named transitions below, so a report cannot be jumped straight to
 * resolved without passing through verification.
 */
export const adminApi = {
  summary: () => request('/admin/summary'),

  listIssues: ({ bucket, category, status, area, search, overdue, sort, page = 0, size = 25 }) =>
    request(
      `/admin/issues${query({
        bucket,
        category,
        status,
        area,
        q: search,
        overdue: overdue ? 'true' : '',
        sort,
        page,
        size,
      })}`,
    ),

  /** Picks up a fresh report and commits to a date. */
  acknowledge: (id, { etaDate, note }) =>
    request(`/admin/issues/${id}/acknowledge`, { method: 'POST', body: { etaDate, note } }),

  /** Revises a date already given. */
  schedule: (id, { etaDate, note }) =>
    request(`/admin/issues/${id}/schedule`, { method: 'POST', body: { etaDate, note } }),

  start: (id) => request(`/admin/issues/${id}/start`, { method: 'POST' }),

  /** Opens the verification window. Does not close the report. */
  markDone: (id) => request(`/admin/issues/${id}/done`, { method: 'POST' }),

  reopen: (id) => request(`/admin/issues/${id}/reopen`, { method: 'POST' }),
};
