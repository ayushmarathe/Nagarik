# Nagarik

A place to report local problems — no water in the morning line, a transformer throwing
sparks, a pothole at the school crossing — and for the people living with the same problem to
back it and add what they know. The number of people backing a report is the point of the
app: one complaint is a nuisance, forty is a queue somebody has to answer for.

The other half is what happens next. A moderator picks a report up and commits to a date in
public, and when they say the work is done, the report does not close — it goes back to the
people who backed it, and their answer is what closes it. "Fixed" is a claim, and the person
making it is not the person living with the problem.

Two interfaces: the board at `/` for residents, and the ward dashboard at `/admin` for the
people clearing the queue. React frontend, Spring Boot API, Postgres database,
email-and-password accounts with signed tokens.

## Looking at it without installing anything

There are two of these, and between them they cover both halves of the app. Open either in a
browser — that is the whole procedure, no database, no Maven, no npm.

`preview.html` is the resident board. `admin-preview.html` is the ward dashboard, the same one
the real app serves at `/admin`. They share `preview-data.js`, which holds one copy of the
fourteen seeded reports so the two cannot disagree about the board they are showing. All three
files sit at the repository root, are not part of the build, and can be deleted without
breaking anything.

Both load the real stylesheets and use the same markup and class names the React components
produce, so they are an accurate picture of the design rather than a mock-up of it. Both themes
work, and the choice carries between the two files and into the real app.

On the board: the filters, the search and the sorts work, backing a report works, and opening
one shows the lifecycle panel — including the two reports inside an open verification window,
whose votes you can cast and change.

On the dashboard: the six queues, the summary cards and the filters work, and so does every
move a moderator can make. Picking a report up asks for a date and publishes it, the date can
be changed, a crew can be sent, work can be marked done, and something that came back can be
reopened. Those moves really do change the board — mark a report done and it moves to
**Being checked** with a week on the clock, and the counts follow it. A window whose week has
run out settles the moment the queue is next read, in the majority's favour, exactly as the
backend settles it.

Nothing in either file talks to a server: signing in, signing out and posting a reply say so
rather than pretending, and the state lives in memory, so a reload restores the seeded board.
One deliberate departure, because the alternative is showing nothing: the real app only lets
people who backed a report answer its verification window, and the previews have nobody signed
in, so they treat you as a backer of those two reports.

The file is not part of the build and deleting it breaks nothing.

## Running it

You need JDK 17 or newer, Maven, Node 18 or newer, and a Postgres you can reach. Docker is
**not** required — if you have Postgres installed, use it.

### 1. A database

Create the database and the user the app expects. With `psql` on your PATH:

```
psql -U postgres -c "CREATE USER nagarik WITH PASSWORD 'nagarik';"
psql -U postgres -c "CREATE DATABASE nagarik OWNER nagarik;"
```

If you installed Postgres on Windows with the EnterpriseDB installer, `psql` lives in
`C:\Program Files\PostgreSQL\16\bin`. You can equally do this in pgAdmin, or use a database
and user you already have — nothing about the names is special, and the next section is how
you point the app somewhere else.

If you would rather run Postgres in a container and you do have Docker, `docker compose up -d`
at the repository root brings up Postgres 16 on port 5432 with the same database, user and
password, keeping its data in a named volume. That is a convenience, not the main path.

### 2. The API

```
cd backend
mvn spring-boot:run
```

It comes up on http://localhost:8080. On first run only — when the issue table is empty — it
seeds 150 residents and 14 reports dated back across a few weeks, with at least one sitting in
every stage of the lifecycle: five waiting to be picked up, two scheduled, two with a crew out,
two inside an open verification window, one the neighbourhood rejected, and two confirmed
fixed. So the dashboard has every tab populated and the board has a live vote to look at
without you having to click a report through five states first. It prints the demo logins to
the console when it does.

There is no `mvnw` wrapper checked in; the wrapper needs a binary jar that could not be
generated here. Use your installed Maven, or open `backend` in IntelliJ or VS Code and run
`NagarikApplication` directly.

### 3. The frontend

In a second terminal:

```
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` to port 8080, so both halves behave as one
origin and there is no CORS step to think about.

The dashboard is at http://localhost:5173/admin. It is a second Vite entry point rather than a
route — `admin.html` and `admin.jsx` alongside `index.html` and `main.jsx` — so the two
interfaces share the tokens and primitives in `styles.css` and nothing else. A resident who
finds the URL gets an explanation rather than a worklist, and every `/api/admin` call behind it
is refused by the server without a moderator role regardless.

## Signing in

The seeded accounts all share one password, `nagarik123`:

| Account | Email | Can do |
| --- | --- | --- |
| Ward Moderator | `moderator@nagarik.test` | Everything a resident can, plus the dashboard: picking reports up, promising dates, marking work done |
| Anita Sharma | `anita.sharma@nagarik.test` | Post, back, reply, and verify a fix on anything she backed |

Every other seeded resident follows the same pattern — `firstname.lastname@nagarik.test`, all
lowercase, drawn from the name lists in `DataSeeder`. Or register a new account in the app;
new registrations are always residents, and the only way to get a moderator is the seeder or
an `UPDATE app_user SET role = 'MODERATOR'`.

`.test` is reserved by RFC 2606, so none of these addresses can accidentally reach a real
inbox.

## Configuration

Every setting is an environment variable with a working default, which is how you point this
at a managed database without editing a file.

| Variable | Default | Notes |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/nagarik` | |
| `DATABASE_USER` | `nagarik` | |
| `DATABASE_PASSWORD` | `nagarik` | |
| `NAGARIK_JWT_SECRET` | a development string | **Change this before it serves anything real** |
| `NAGARIK_JWT_EXPIRY_HOURS` | `168` (a week) | |

The signing key is the one that matters. Anyone holding it can mint a token for any account,
including the moderator. The default in `application.properties` is committed to this
repository and is therefore not a secret in any sense. HS256 needs at least 32 bytes, and the
app refuses to start with a shorter one rather than failing at the first login.

Generate one in PowerShell:

```powershell
$b = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
[Convert]::ToBase64String($b)
```

Then set it for the session before starting the API:

```powershell
$env:NAGARIK_JWT_SECRET = "the-value-you-just-generated"
```

On macOS or Linux, `openssl rand -base64 48` and `export`.

## How authentication works

Registering or signing in returns a JSON Web Token. The browser keeps it in `localStorage` and
sends it as `Authorization: Bearer <token>` on every request. `JwtAuthFilter` verifies the
signature, then loads the account from the database rather than trusting the name and role
baked into the token — one lookup by primary key, in exchange for a deleted account failing
immediately and a role change taking effect on the next click instead of at token expiry.

Passwords are BCrypt at strength 10. A failed sign-in says the same thing whether the address
is unknown or the password is wrong, and the unknown-address path still pays for a BCrypt
comparison against a dummy hash, so the two cases take the same time. For an app that shows
your name and your street, "is this person registered here" is a question worth not answering.

Reading is public and always has been: the board, the tallies and the discussions need no
account. Posting, backing and replying need one. Every transition in the lifecycle below needs
a moderator, and that is checked twice — once as a route rule on `/api/admin/**` in
`SecurityConfig`, and again inside `IssueService` before each transition runs. The second check
is the one that survives somebody adding a route later and forgetting to list it.

What this deliberately does not do: there is no refresh token, no server-side session and no
way to revoke a token before it expires. Signing out means the browser discarding it. A
token that leaks stays good for up to a week, which is the honest cost of statelessness — if
that trade stops being acceptable, the replacement is a short-lived token plus a refresh
token you can revoke, not a longer expiry.

## The lifecycle

```
REPORTED ──pick up──> ACKNOWLEDGED ──crew out──> IN_PROGRESS
                            │                         │
                            └────── mark done ────────┘
                                         │
                                   VERIFYING  ← the backers answer
                                    │      │
                             agreed │      │ did not
                                    ▼      ▼
                              RESOLVED   DISPUTED ──go again──> IN_PROGRESS
```

Picking a report up requires a date. It is not optional, and it goes straight onto the public
report along with an optional note — a promise nobody can see is not a promise. Once the date
passes, the report is flagged as overdue on the board and on the dashboard, and it stays
flagged; the promise is not quietly rewritten by the next edit.

Marking the work done does not close the report. It opens a seven-day window and hands the
report back to **the people backing it** — nobody else can answer, because nobody else has been
living with it. A moderator has no action available while a window is open, which is the point
of the whole arrangement.

A window settles two ways:

- **Early**, when enough people have answered. "Enough" is the smaller of five and the number
  of backers, so a report with two backers can settle on two answers instead of waiting a week
  for a fifth that is never coming. A strict majority is required; a tie keeps the window open.
- **At the deadline**, on whoever is ahead. Here a tie goes to resolved, and so does a window
  nobody answered at all — after a week of silence the claim stands.

Agreement gives RESOLVED. Disagreement gives DISPUTED, which is not a terminal state: it goes
back on the dashboard as work owed, keeps its backing, and the moderator schedules it again. A
report the neighbourhood says is unfixed counts as more open than one nobody has looked at yet,
which is why `IssueStatus.isOpen()` is "anything except RESOLVED".

Deadlines are settled by a sweep on the way into any read rather than by a scheduled job.
`@Scheduled` only fires while the application happens to be running, so a laptop closed over a
weekend would come back with windows that expired on Saturday still sitting open; sweeping on
read means whatever you are about to look at has already been brought up to date, however long
the process was down. It is two indexed bulk updates that usually match nothing, throttled to
at most one pass every thirty seconds.

There is no "set the status to X" endpoint anywhere, on purpose. Each transition is its own
named route with its own preconditions, so a report cannot be jumped straight from REPORTED to
RESOLVED without passing through a verification window — the illegal move is not expressible
rather than merely rejected.

## The dashboard

`/admin` is a worklist, not a feed. Six tabs over the same queue — needs a date, scheduled,
being checked, disputed, closed, everything — with five counters across the top that are
buttons rather than statistics, because a number you cannot click is a number somebody has to
go and find the rows for by hand.

The buttons on each row are exactly the transitions the server will accept from that status.
That list is duplicated from `IssueService` into `QueueRow` deliberately: a button that answers
409 teaches people to distrust the interface, and the alternative — asking the server what is
legal — is a round trip per row.

Acting on a report does not pull it out of the queue. It dims and says it will leave the tab on
the next read, because yanking a row out from under the cursor loses the place of whoever is
working down a list of twenty.

## How the rest fits together

The API does the work. The feed is filtered, sorted and paged in Postgres and comes back as a
page object — `items`, `page`, `size`, `totalItems`, `totalPages`, `hasMore` — with everything
a row needs to render, including `votedByMe`, so the browser never has to work out whether you
have already backed something.

Filtering uses the Criteria API through `JpaSpecificationExecutor` rather than a derived query
per combination. There are four optional filters (category, status, locality, text), which as
derived methods would be fifteen almost-identical queries; as specifications, each one adds a
predicate only when it is actually present, and an absent filter costs nothing.

Every sort ends with `id` as a tiebreaker. Without it, two reports with the same number of
backings can swap places between one request and the next, and a report you already read shows
up again on the next page while another one is never shown at all.

Categories and statuses are served from `/api/categories` and `/api/statuses` rather than
hardcoded in JavaScript. Adding a value to the `Category` enum makes it appear in the filter
legend, the tallies and the report form without touching the frontend.

Vote counts are stored on the issue row for cheap sorting, but the `vote` table is the source
of truth: a unique constraint on (issue, user) is what stops one person clicking a report to
the top, and the counter is recomputed from that table after every toggle rather than
incremented, so it cannot drift.

The `confirmation` table works the same way and for the same reason — one row per person per
report, with the confirmed and denied counters recomputed from it after every answer. Changing
your mind updates the row rather than adding a second one, which is what lets an answer stay
changeable until the window closes. Reopening a report deletes its confirmations outright: the
second attempt at a repair has to be judged on its own, not against answers people gave about
the first one.

Eligibility to answer is read from the vote table at the moment of answering rather than from a
snapshot taken when the window opened. That is what makes withdrawing your backing also give up
your say on the fix, which is the behaviour you want — but it cuts the other way too: backing a
report while its window is open earns a vote on the outcome immediately. For a ward app where
the backers are the neighbours living with the problem that is a fair trade, and it is a real
hole if this is ever pointed at anything contested. The fix, if it needs one, is to stamp
eligibility at `verifyOpenedAt` and check votes against that instant.

### Schema and migrations

`ddl-auto` is set to `update`, so Hibernate creates and alters tables to match the entities.
That is the right trade for a prototype. Before this carries real data, move the schema into
Flyway migrations and set `ddl-auto=validate`, so an accidental entity change cannot silently
rewrite a live table. The email, password and role columns on `app_user` are nullable for
exactly this reason — `update` cannot add a non-null column to a table that already has rows.

## API

| Method | Path | Who | What it does |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | anyone | Create an account, returns a token |
| `POST` | `/api/auth/login` | anyone | Sign in, returns a token |
| `GET` | `/api/auth/me` | signed in | Who the token belongs to, freshly read |
| `PATCH` | `/api/auth/me` | signed in | Change your display name |
| `GET` | `/api/issues?category=&status=&area=&q=&sort=&page=&size=` | anyone | One page of the feed. `sort` is `top`, `new`, or `discussed`; `q` searches title, description and locality; `size` defaults to 20 and is capped at 100 |
| `GET` | `/api/issues/stats` | anyone | Totals and the per-category and per-status tallies |
| `GET` | `/api/issues/areas` | anyone | Localities that exist, for the picker |
| `GET` | `/api/issues/{id}` | anyone | One report |
| `POST` | `/api/issues` | signed in | File a report |
| `POST` | `/api/issues/{id}/vote` | signed in | Back it, or take your backing away |
| `POST` | `/api/issues/{id}/confirm` | a backer | Answer whether the repair happened. `{"fixed": true}`. Changeable until the window closes |
| `GET` | `/api/issues/{id}/comments` | anyone | The discussion |
| `POST` | `/api/issues/{id}/comments` | signed in | Reply |
| `GET` | `/api/categories`, `/api/statuses` | anyone | Values for filters and pickers |

The moderator's surface, all of it behind `hasAnyRole("MODERATOR", "ADMIN")`:

| Method | Path | What it does |
| --- | --- | --- |
| `GET` | `/api/admin/summary` | The five counters, plus both tallies |
| `GET` | `/api/admin/issues?bucket=&category=&status=&area=&q=&overdue=&sort=&page=&size=` | The queue. `bucket` is `inbox`, `owed`, `verifying`, `disputed`, `resolved` or `open`; absent means everything. `sort` adds `eta` and `deadline` to the board's options |
| `POST` | `/api/admin/issues/{id}/acknowledge` | Pick it up and commit to a date. `{"etaDate": "2026-09-20", "note": "..."}` |
| `POST` | `/api/admin/issues/{id}/schedule` | Revise a date already given. Same body |
| `POST` | `/api/admin/issues/{id}/start` | Work has started |
| `POST` | `/api/admin/issues/{id}/done` | Claim it is finished, which opens the verification window |
| `POST` | `/api/admin/issues/{id}/reopen` | Put a settled report back on the pile, discarding its answers |

Confirming a fix is on the public controller rather than under `/api/admin` because it is a
resident action — the one part of the workflow a moderator cannot do on their own behalf. Who
may answer is decided by looking for their backing, not by a role.

An unknown `bucket` falls through to no filter rather than to a 400. The parameter only ever
narrows a list, so the worst a typo can do is show more than was asked for.

Failures come back as `{"message": "..."}`, with a `fields` map added when a form was the
problem, so the interface can show the message it is given without rewriting it. A request
with no token, or a stale one, gets 401 with the same shape; wrong role gets 403; a transition
the report is not in a state for gets 409.

`/api/issues/stats` and `/api/issues/areas` are literal paths, so Spring matches them ahead of
`/api/issues/{id}` and there is no route collision to worry about.

## Design

Deep teal carries the interface and marigold is held in reserve for one thing only: the number
of people backing a report. It is the largest, heaviest, loudest element on any row, because it
is the one fact the app exists to show. Everything else — status, category, locality, time,
reply count — is set quietly around it. The verification state is the single sanctioned
exception, because a report waiting on an answer from you is the one other thing worth
interrupting for.

The dashboard is deliberately a different kind of surface. It shares the palette, the fonts and
the primitives, and nothing else: tighter radii, hairline rules instead of shadows, tabular
numbers, rows sized to fit rather than to breathe. The board is built for reading and the
dashboard for clearing, and a moderator working through twenty reports should be able to see
twenty reports. Every class in `admin.css` is prefixed `adm-`, since both stylesheets load on
that page.

Two typefaces. Bricolage Grotesque, which is slightly odd and tightly set, takes the wordmark,
the headings and every number. Public Sans, drawn for US federal government use, takes
everything you read rather than scan. The pairing is meant to feel civic without feeling like
a form.

Corner radius and elevation step down with importance rather than being one value applied
everywhere: dialogs are the roundest and float highest, cards sit almost flat, chips are nearly
square. In dark mode card shadows are removed altogether and the borders do the lifting, since
a shadow on a dark canvas mostly just smudges.

Dark mode follows the system by default and can be toggled; the choice is remembered. A small
blocking script in `index.html` sets the theme attribute before first paint, which is the only
way to stop a dark-mode reader getting a white flash on every load — it duplicates two lines
from `lib/theme.js` on purpose, because a module import is deferred by definition and would put
the flash back.

The board grows in one step. Below 56rem it is a single column with the filters folded behind a
button, because on a phone the feed is the point and a permanently open sidebar is just
scrolling you have to do before you reach anything. At 56rem the filters and tallies become a
sticky sidebar beside the feed. Below 34rem the spacing tightens. The feed rows come in on a
single short stagger on load, capped so the last row is not still animating when you reach it,
and the whole motion budget is switched off under `prefers-reduced-motion`.

## Not built yet

Photo attachments, map pins, email verification and password reset, a profile page listing your
own reports, notifications when a report you backed changes status or opens for checking, and
moderation of reports that are spam or abusive. Nothing notifies a backer that a window has
opened, which is the largest gap in the verification loop as it stands — today they find out by
visiting the board. The category enum is the natural place to start extending; the status enum
is not, since every value in it now has transitions and an interface built around it.

## Dead files you can delete

Three files are empty or route-less placeholders, kept only because they could not be deleted
from where this was written. Nothing imports or maps them:

- `backend/src/main/java/com/nagarik/web/UserController.java`
- `backend/src/main/java/com/nagarik/web/dto/UserRequest.java`
- `frontend/src/components/NameGate.jsx`

All three are leftovers from the pre-authentication design, where signing in meant typing a
name and the browser sent the returned id as an `X-User-Id` header the server simply believed.

## A caveat on this cut

The sandbox that normally compiles and runs code has been unavailable throughout — a Windows
update stopped it reaching files on this machine — so **none of this has been executed**. Not
`mvn`, not `npm`, not a single request, and not one look at either interface in a browser at
any screen size. Every file has been read through for wiring errors, signatures checked against
their call sites, request parameters checked against the client that sends them, and every CSS
class the new components reference checked against a rule that defines it. That process has
found real bugs — an unhandled `/error` dispatch answering 401 instead of the actual status, a
`__dirname` in an ESM Vite config, a class-name collision that would have clipped the whole
progress panel to six pixels — but it is not a substitute for running the thing.

One known failure is outstanding: the backend does not currently start, throwing
`NoUniqueBeanDefinitionException` during context startup. Run `mvn spring-boot:run > run.log
2>&1` and the log will name the type with two candidate beans.
