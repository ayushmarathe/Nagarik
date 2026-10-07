/*
  THE SEEDED BOARD, SHARED BY BOTH PREVIEWS.

  preview.html (the resident board) and admin-preview.html (the ward dashboard)
  both load this file. They used to be sold as one self-contained file each,
  which meant the same fourteen reports were written out twice - and two copies
  of the same data drift. This is the one copy.

  It is a plain script, not a module, so it works when the previews are opened
  straight off the disk with no server. Modules would need one.

  The contents mirror DataSeeder.java on first run. Field names mirror
  IssueResponse.java, so anything read here is read by the same name the API
  sends it under.

  Not part of the build. Nothing in frontend/ or backend/ imports it.
*/

var DAY = 86400000;

var CATEGORIES = [
  { value: 'ELECTRICITY', label: 'Electricity' },
  { value: 'WATER', label: 'Water supply' },
  { value: 'ROADS', label: 'Roads and footpaths' },
  { value: 'DRAINAGE', label: 'Drainage and sewage' },
  { value: 'GARBAGE', label: 'Garbage collection' },
  { value: 'STREETLIGHT', label: 'Street lighting' },
  { value: 'OTHER', label: 'Something else' }
];

/* IssueStatus.java, label for label. "Fixed" rather than "Resolved" and "Work
   started" rather than "In progress" are the enum's own words. */
var STATUS_LABEL = {
  REPORTED: 'Reported',
  ACKNOWLEDGED: 'Acknowledged',
  IN_PROGRESS: 'Work started',
  VERIFYING: 'Checking the fix',
  RESOLVED: 'Fixed',
  DISPUTED: 'Still not fixed'
};

var MODERATOR = 'Ward Moderator';

/* IssueService: the window is a week, and settles early at five answers or the
   number of people backing it, whichever is smaller. */
var VERIFY_WINDOW_DAYS = 7;
var VERIFY_QUORUM_CAP = 5;

function categoryColor(name) {
  var known = {
    ELECTRICITY: 'var(--electricity)',
    WATER: 'var(--water)',
    ROADS: 'var(--roads)',
    DRAINAGE: 'var(--drainage)',
    GARBAGE: 'var(--garbage)',
    STREETLIGHT: 'var(--streetlight)',
    OTHER: 'var(--other)'
  };
  return known[name] || 'var(--ink-faint)';
}

function categoryLabel(value) {
  for (var i = 0; i < CATEGORIES.length; i += 1) {
    if (CATEGORIES[i].value === value) return CATEGORIES[i].label;
  }
  return value;
}

/*
  The server sends etaDate as a bare "YYYY-MM-DD" with no timezone, so the
  previews build the same shape rather than a Date. That is what lets the
  helpers below be copied from lib/format.js unchanged.

  Built from local date parts on purpose. toISOString() would give the UTC day,
  which east of Greenwich is tomorrow for part of every evening - so a date
  picked at 9pm would come back a day later than the one clicked.
*/
function dateStr(offsetDays) {
  var d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() + offsetDays);
  return (
    d.getFullYear() +
    '-' + String(d.getMonth() + 1).padStart(2, '0') +
    '-' + String(d.getDate()).padStart(2, '0')
  );
}

function daysAgo(n) {
  return new Date(Date.now() - n * DAY);
}

/* ---------------------------------------------------------------------------
   lib/format.js, copied rather than approximated
   --------------------------------------------------------------------------- */
function plural(count, word) {
  return count === 1 ? word : word + 's';
}

function timeAgo(date) {
  var seconds = Math.round((Date.now() - date.getTime()) / 1000);
  if (seconds < 60) return 'just now';
  var minutes = Math.round(seconds / 60);
  if (minutes < 60) return minutes + ' ' + plural(minutes, 'minute') + ' ago';
  var hours = Math.round(minutes / 60);
  if (hours < 24) return hours + ' ' + plural(hours, 'hour') + ' ago';
  var days = Math.round(hours / 24);
  if (days < 30) return days + ' ' + plural(days, 'day') + ' ago';
  var months = Math.round(days / 30);
  if (months < 12) return months + ' ' + plural(months, 'month') + ' ago';
  var years = Math.round(months / 12);
  return years + ' ' + plural(years, 'year') + ' ago';
}

function fullDate(date) {
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' });
}

function parseLocalDate(value) {
  if (!value) return null;
  var parts = String(value).split('-').map(Number);
  if (!parts[0] || !parts[1] || !parts[2]) return null;
  return new Date(parts[0], parts[1] - 1, parts[2]);
}

function shortDate(value) {
  var date = parseLocalDate(value);
  if (!date) return '';
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

function daysUntil(value) {
  var date = parseLocalDate(value);
  if (!date) return null;
  var today = new Date();
  today.setHours(0, 0, 0, 0);
  return Math.round((date.getTime() - today.getTime()) / DAY);
}

function dueLabel(value) {
  var days = daysUntil(value);
  if (days === null) return '';
  if (days === 0) return 'due today';
  if (days === 1) return 'due tomorrow';
  if (days > 1) return 'due in ' + days + ' days';
  if (days === -1) return '1 day late';
  return Math.abs(days) + ' days late';
}

function timeLeft(date) {
  var seconds = Math.round((date.getTime() - Date.now()) / 1000);
  if (seconds <= 0) return 'closing now';
  var hours = Math.round(seconds / 3600);
  if (hours < 1) return 'under an hour left';
  if (hours < 36) return hours + ' ' + plural(hours, 'hour') + ' left';
  var days = Math.round(hours / 24);
  return days + ' ' + plural(days, 'day') + ' left';
}

function escapeHtml(text) {
  return String(text).replace(/[&<>"']/g, function (ch) {
    return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
  });
}

/* Only ACKNOWLEDGED, IN_PROGRESS and DISPUTED carry a promised date - the same
   three IssueStatus.acceptsEta() allows one to be set on. */
function isPromised(issue) {
  return Boolean(
    issue.etaDate && ['ACKNOWLEDGED', 'IN_PROGRESS', 'DISPUTED'].indexOf(issue.status) !== -1
  );
}

/* ---------------------------------------------------------------------------
   The fourteen reports

   One per seed(...) call in DataSeeder, in the same order and with the same
   numbers. The short keys are the seeder's arguments; the derivation below
   turns them into the fields IssueResponse actually sends.

     days    how many days ago it was raised
     ack     how many days ago a moderator picked it up
     eta     offset in days for the promised date; negative is overdue
     opened  how many days ago the verification window opened
     yes/no  confirmation rows, which are real rows in the seeder too
     done    how many days ago it was closed
   --------------------------------------------------------------------------- */
var ISSUES = [
  {
    id: 1, category: 'ELECTRICITY', status: 'REPORTED', days: 2, votes: 47,
    title: 'Transformer by the water tank sparks every evening',
    area: 'Sector 4, beside the vegetable market',
    author: 'Prakash Sharma',
    description: 'Around 7pm it starts crackling and throwing sparks onto the footpath below. Children walk under it coming back from tuition. Two shopkeepers have already called the line office and nobody has come to look at it.',
    comments: [
      ['Rekha Sharma', 'It did the same thing last monsoon. They tightened something and left.'],
      ['Manoj Sharma', "I have the junior engineer's number from a previous complaint. Sharing it in the group."],
      ['Pooja Sharma', 'Please put a barricade under it at least until someone comes. Somebody is going to get hurt.']
    ]
  },
  {
    id: 2, category: 'ROADS', status: 'REPORTED', days: 5, votes: 86,
    title: 'Pothole at the school crossing is deep enough to throw a scooter',
    area: "Station Road, outside the girls' school gate",
    author: 'Manoj Thapa',
    description: 'It has been growing since the rain and is now about a foot deep, right where everyone slows down to let children cross. A delivery rider went down on Tuesday morning. It fills with water so you cannot judge the depth.',
    comments: [
      ['Devendra Sharma', 'Someone has put a branch in it as a warning. That is all the warning there is.'],
      ['Kavita Sharma', "Photos from this morning show it has widened again after last night's rain."],
      ['Suresh Sharma', 'The school has written to the ward twice. Happy to add our names to a third letter.']
    ]
  },
  {
    id: 3, category: 'WATER', status: 'REPORTED', days: 3, votes: 52,
    title: 'Supply runs brown for the first ten minutes every morning',
    area: 'Ward 12, Ganga Nagar',
    author: 'Suresh Gurung',
    description: 'It clears if you let it run, but nobody should have to pour away twenty litres before they can cook. Started around the same time as the pump work.',
    comments: [
      ['Arun Gurung', 'Ours runs brown too. We have been filtering it but the filter clogs in days.'],
      ['Lakshmi Gurung', 'Worth asking whether the tank was cleaned after the pump was opened up.']
    ]
  },
  {
    id: 4, category: 'OTHER', status: 'REPORTED', days: 4, votes: 27,
    title: 'Stray dog pack has settled behind the market',
    area: 'Sector 4, beside the vegetable market',
    author: 'Anita Rai',
    description: 'Eight or nine dogs, and two have started following children. Nobody wants them hurt - we want them collected, vaccinated and rehomed properly.',
    comments: [
      ['Rekha Shrestha', 'Please let us keep this humane. There is a rescue in the next ward that does catch and release.'],
      ['Manoj Shrestha', 'I have their number, will call on Monday and report back.']
    ]
  },
  {
    id: 5, category: 'ROADS', status: 'REPORTED', days: 1, votes: 12,
    title: 'Speed breaker worn flat outside the primary school',
    area: 'Hospital Road, near the diagnostic centre',
    author: 'Nisha Rai',
    description: 'It was repainted in the spring but the hump itself has worn down to almost nothing. Traffic comes past the school gate at the same speed as the main road.',
    comments: [
      ['Pooja Verma', 'Paint is not a speed breaker. It needs rebuilding, not remarking.']
    ]
  },
  {
    id: 6, category: 'ROADS', status: 'ACKNOWLEDGED', days: 30, votes: 71,
    ack: 3, eta: 9,
    workNote: 'Measured on site. Slabs are being cut to size at the yard.',
    title: 'Footpath slabs lifted outside the clinic',
    area: 'Hospital Road, near the diagnostic centre',
    author: 'Gopal Gurung',
    description: 'Three slabs have risen about four inches where a root has pushed under them. People coming out of the clinic with sticks and frames have to step into the road to get past.',
    comments: [
      ['Pooja Sharma', 'My mother fell here last month. We did not report it, which I now regret.'],
      ['Arun Sharma', 'Ward engineer came and measured on Thursday, so something is moving.']
    ]
  },
  {
    /* The overdue one. Highest-backed report still open, promised for a date
       that has now passed - the row the dashboard's overdue counter exists to
       surface, and the one that turns the feed chip red. */
    id: 7, category: 'DRAINAGE', status: 'ACKNOWLEDGED', days: 25, votes: 103,
    ack: 12, eta: -5,
    workNote: 'Replacement cover ordered from the depot. Barricade in place meanwhile.',
    title: 'Manhole cover missing at the junction',
    area: 'Station Road, at the Lane 3 junction',
    author: 'Lakshmi Shrestha',
    description: 'The cover has been gone for over a month. Someone has stood a bamboo pole in it but at night you cannot see the pole either. This is on the route children take to school.',
    comments: [
      ['Nisha Shrestha', 'This is the most dangerous thing on this list. Please do not let it drop.'],
      ['Bikash Shrestha', 'Ward office says a replacement cover is ordered. Asked them to barricade it meanwhile.'],
      ['Rekha Shrestha', 'Barricade appeared yesterday. Still no cover.']
    ]
  },
  {
    id: 8, category: 'WATER', status: 'IN_PROGRESS', days: 9, votes: 118,
    ack: 6, eta: 4,
    workNote: 'Replacement motor fitted, testing the line. Tankers continue twice daily until then.',
    title: 'No morning supply in Ward 12 for nine days now',
    area: 'Ward 12, Ganga Nagar',
    author: 'Manoj Sharma',
    description: 'The morning line has been dry since the 1st. Tankers come once every three days and there is a scramble every time. Households at the end of the lane get nothing at all. The board says a pump is being repaired but there is no date.',
    comments: [
      ['Kavita Thapa', 'Confirmed at the ward office today - pump motor burnt out, replacement is on order.'],
      ['Suresh Thapa', 'Nine days without a date is not a repair, it is a shrug. Can we ask for tanker timings in writing?'],
      ['Nisha Thapa', 'Tanker came at 6am today without notice and left before half the lane knew.'],
      ['Bikash Thapa', 'If someone can share the ward office email I will write asking for a published schedule.']
    ]
  },
  {
    id: 9, category: 'STREETLIGHT', status: 'IN_PROGRESS', days: 7, votes: 94,
    ack: 4, eta: 2,
    workNote: 'Contractor on site. Two fittings replaced, four to go.',
    title: 'Six street lights dark on the stretch to the highway',
    area: 'Bypass Road, between the petrol pump and the flyover',
    author: 'Prakash Thapa',
    description: 'The entire stretch past the petrol pump is unlit. Women walking back from the evening shift at the garment unit have started taking the longer route through the colony instead because this one feels unsafe.',
    comments: [
      ['Meera Gurung', 'Ward office has logged it and says the contractor will attend this week. Holding them to that.'],
      ['Devendra Gurung', 'Two of the six came on last night. Four still dark.']
    ]
  },
  {
    /* Marked done, window open, four answers in and one short of quorum. */
    id: 10, category: 'DRAINAGE', status: 'VERIFYING', days: 14, votes: 34,
    ack: 9, opened: 2, yes: 3, no: 1, backed: true,
    workNote: 'Both corners rodded and the silt trap emptied.',
    title: 'Drain overflows across the footpath after every shower',
    area: 'Lane 3, Shanti Colony',
    author: 'Bikash Verma',
    description: 'The drain at the corner backs up within minutes of rain starting and spreads across the whole footpath, so people walk in the road instead. It has not been cleared since before the last festival.',
    comments: [
      ['Ravi Verma', 'Same at the other end of the lane. Whatever is blocking it is between the two corners.'],
      ['Sunita Verma', 'Corner drain runs clear now. Waiting on the next shower to be sure.'],
      ['Prakash Verma', 'Mine is the no vote - the far end still pools even though the corner is fine.']
    ]
  },
  {
    /* Nobody has answered this one, which is the state the board has to render
       before anybody has pressed anything. */
    id: 11, category: 'STREETLIGHT', status: 'VERIFYING', days: 6, votes: 18,
    ack: 4, opened: 1, yes: 0, no: 0, backed: true,
    workNote: 'Choke replaced on the pole. Please confirm once it has been through a full night.',
    title: 'The light at the bus stop flickers all night',
    area: 'Ward 9, behind the bus depot',
    author: 'Devendra Joshi',
    description: 'It never fully fails so it never gets reported, but it strobes from dusk to dawn. The families in the two houses opposite have been sleeping with the curtains taped shut.',
    comments: [
      ['Prakash Verma', 'A flickering light is still counted as a working light, which is why this never gets fixed.']
    ]
  },
  {
    /* The neighbourhood said the repair did not hold. Back on the pile with its
       new promised date already behind it. */
    id: 12, category: 'GARBAGE', status: 'DISPUTED', days: 11, votes: 41,
    ack: 9, opened: 4, eta: -3, yes: 1, no: 4,
    workNote: 'Bins emptied and the park added to the Tuesday round.',
    title: 'Nobody has emptied the bins at the park gate since the festival',
    area: 'Shanti Colony park, main gate',
    author: 'Suresh Shrestha',
    description: 'Both bins have been overflowing for a fortnight and the overflow has spread into the hedge. It smells from across the road and the morning walkers have started going elsewhere.',
    comments: [
      ['Kavita Patel', 'I called the sanitation number twice. Both times they said the park is not on their list.'],
      ['Suresh Patel', "If the park is not on anyone's list then who put the bins there?"],
      ['Nisha Patel', 'They emptied the bins and left everything that had spilled into the hedge. Voting no.']
    ]
  },
  {
    id: 13, category: 'GARBAGE', status: 'RESOLVED', days: 21, votes: 29,
    ack: 14, opened: 13, done: 6, yes: 5, no: 0,
    workNote: 'Lane restored to the alternate-morning route.',
    title: 'Collection van stopped coming down our lane',
    area: 'Ward 9, behind the bus depot',
    author: 'Nisha Sharma',
    description: 'The van used to come on alternate mornings and stopped about three weeks ago. Waste is piling up at the lane mouth and dogs pull it apart overnight.',
    comments: [
      ['Arun Verma', 'The van driver told me the lane was dropped from the route by mistake when the schedule changed.'],
      ['Lakshmi Verma', 'Collection restarted on Monday. Marking this resolved - thanks to everyone who called.']
    ]
  },
  {
    id: 14, category: 'ELECTRICITY', status: 'RESOLVED', days: 40, votes: 63,
    ack: 30, opened: 19, done: 12, yes: 4, no: 1,
    workNote: 'Faulty section of the feeder replaced.',
    title: 'Power cuts every afternoon between two and four',
    area: 'Sector 4, beside the vegetable market',
    author: 'Nisha Patel',
    description: 'Daily, almost to the minute. The shops with cold storage are losing stock and nobody has told us whether it is load shedding or a fault.',
    comments: [
      ['Pooja Thapa', 'It was a fault on the feeder, fixed on the 14th. Two weeks of clear afternoons since.']
    ]
  }
];

/* Turn the seeder's shorthand into the fields IssueResponse sends, under the
   names it sends them under. */
ISSUES.forEach(function (issue) {
  issue.createdAt = daysAgo(issue.days);
  issue.voteCount = issue.votes;
  issue.commentCount = issue.comments.length;
  issue.myConfirmation = null;
  issue.statusLabel = STATUS_LABEL[issue.status];
  issue.categoryLabel = categoryLabel(issue.category);

  if (issue.ack !== undefined) {
    issue.acknowledgedAt = daysAgo(issue.ack);
    issue.acknowledgedBy = MODERATOR;
  }
  if (issue.eta !== undefined) issue.etaDate = dateStr(issue.eta);
  if (issue.opened !== undefined) {
    issue.verifyOpenedAt = daysAgo(issue.opened);
    issue.verifyDeadline = new Date(issue.verifyOpenedAt.getTime() + VERIFY_WINDOW_DAYS * DAY);
    issue.verifyQuorum = Math.min(VERIFY_QUORUM_CAP, issue.voteCount);
  }
  if (issue.done !== undefined) issue.resolvedAt = daysAgo(issue.done);

  issue.confirmedCount = issue.yes || 0;
  issue.deniedCount = issue.no || 0;
  issue.overdue = isPromised(issue) && daysUntil(issue.etaDate) < 0;

  // Nobody is signed in here, so votedByMe is false for everything except the
  // reports the previews deliberately treat you as a backer of - otherwise the
  // verification vote, which is the whole point of the VERIFYING stage, would
  // have nothing to show. The one lie, and it is documented in the README.
  issue.votedByMe = Boolean(issue.backed);

  // Derived, never written down. IssueService sends canVerify as exactly this
  // pair of conditions, so hardcoding it is how you end up with a row that
  // claims you may vote while also claiming you never backed it.
  issue.canVerify = issue.status === 'VERIFYING' && issue.votedByMe;
});

/*
  CLOSING A VERIFICATION WINDOW.

  IssueService settles a window two ways, and both are reproduced here because a
  stage that can be entered and never left is not the feature that was asked
  for - mark something done in the dashboard and it has to be able to come back
  as Fixed or as Still not fixed.

  Early, the moment an answer arrives: once as many people have answered as the
  quorum asked for, and the two sides are not level, the majority decides. A tie
  never settles early - it waits, because the next answer breaks it.

  On the deadline: after a week, whatever is in hand decides, and a tie goes to
  the moderator. So does a window nobody answered. That is a deliberate choice
  in the backend rather than an oversight - the alternative is that silence
  reopens work which may well have been done, and the report can still be
  reopened later by anyone who finds otherwise.

  The real backend sweeps for expired windows on the way into every read rather
  than on a timer, and throttles itself to one sweep every 30 seconds. Here
  sweepDeadlines() is called at the top of a render for the same reason: it has
  to run before anything reads a status.
*/
function settleEarly(issue) {
  if (issue.status !== 'VERIFYING') return false;

  var answered = issue.confirmedCount + issue.deniedCount;
  if (issue.verifyQuorum <= 0 || answered < issue.verifyQuorum) return false;
  if (issue.confirmedCount === issue.deniedCount) return false;

  return settleAs(issue, issue.confirmedCount > issue.deniedCount);
}

function sweepDeadlines() {
  var settled = [];
  ISSUES.forEach(function (issue) {
    if (issue.status !== 'VERIFYING') return;
    if (issue.verifyDeadline.getTime() > Date.now()) return;
    // Ties and silence both go to the moderator: >= , not >.
    if (settleAs(issue, issue.confirmedCount >= issue.deniedCount)) settled.push(issue);
  });
  return settled;
}

/* Not named close(). This is a plain script, so a top-level function named
   close would replace window.close. */
function settleAs(issue, confirmed) {
  issue.status = confirmed ? 'RESOLVED' : 'DISPUTED';
  issue.statusLabel = STATUS_LABEL[issue.status];

  if (confirmed) issue.resolvedAt = new Date();
  else delete issue.resolvedAt;

  // Neither status carries a promised date, so a stale one stops counting as
  // late. DISPUTED does accept a new one, once a moderator sets it.
  issue.overdue = isPromised(issue) && daysUntil(issue.etaDate) < 0;
  issue.canVerify = false;
  return true;
}

/* Answering the window. Only people who backed the report may, which is checked
   here rather than trusted from a flag, and an answer can be changed while the
   window is open. Mirrors IssueService.confirmFix. */
function confirmFix(issue, confirmed) {
  if (issue.status !== 'VERIFYING' || !issue.votedByMe) return false;

  var before = issue.myConfirmation;
  if (before === confirmed) return false;

  if (before === true) issue.confirmedCount -= 1;
  if (before === false) issue.deniedCount -= 1;
  if (confirmed) issue.confirmedCount += 1;
  else issue.deniedCount += 1;

  issue.myConfirmation = confirmed;
  settleEarly(issue);
  return true;
}

/* Every locality on the board, for the filter lists. GET /api/issues/areas
   returns these sorted, so the previews do not sort them again. */
var AREAS = (function () {
  var seen = [];
  ISSUES.forEach(function (issue) {
    if (seen.indexOf(issue.area) === -1) seen.push(issue.area);
  });
  return seen.sort();
})();

/*
  IssueService.stats() and adminSummary(), computed rather than written down.
  Both previews had these as hardcoded numbers in the markup, which was fine
  until an action on the dashboard changed one of them.

  residents is users.count(), which includes the moderator - so 151, not the 150
  residents the seeder creates. The name is the backend's.
*/
function countBy(status) {
  return ISSUES.filter(function (issue) { return issue.status === status; }).length;
}

function stats() {
  var total = ISSUES.length;
  var resolved = countBy('RESOLVED');
  var backings = ISSUES.reduce(function (sum, issue) { return sum + issue.voteCount; }, 0);

  return {
    total: total,
    open: total - resolved,
    resolved: resolved,
    unacknowledged: countBy('REPORTED'),
    overdue: ISSUES.filter(function (issue) { return issue.overdue; }).length,
    awaitingVerification: countBy('VERIFYING'),
    disputed: countBy('DISPUTED'),
    votes: backings,
    residents: 151
  };
}

/* The theme, shared so a choice made in either preview carries into the other
   and into the real app, which reads the same key. */
function readTheme() {
  try {
    var stored = localStorage.getItem('nagarik.theme');
    if (stored === 'light' || stored === 'dark') return stored;
  } catch (e) {}
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  document.documentElement.style.colorScheme = theme;
  try { localStorage.setItem('nagarik.theme', theme); } catch (e) {}
}

var THEME_SUN =
  '<svg width="16" height="16" viewBox="0 0 17 17" aria-hidden="true" focusable="false">' +
  '<circle cx="8.5" cy="8.5" r="3.4" fill="currentColor"/>' +
  '<g stroke="currentColor" stroke-width="1.5" stroke-linecap="round">' +
  '<path d="M8.5 .8v2M8.5 14.2v2M.8 8.5h2M14.2 8.5h2M3 3l1.4 1.4M12.6 12.6 14 14M14 3l-1.4 1.4M4.4 12.6 3 14"/>' +
  '</g></svg>';

var THEME_MOON =
  '<svg width="16" height="16" viewBox="0 0 17 17" aria-hidden="true" focusable="false">' +
  '<path d="M14.5 10.4A6.4 6.4 0 0 1 6.6 2.5a6.5 6.5 0 1 0 7.9 7.9Z" fill="currentColor"/></svg>';
