# DevHabit — Project Plan

**Owner:** Nayeem · **Last rewritten:** 2026-10-10 (after the product and code review,
`docs/REVIEW-2026-10-09.md`)

| File | Holds | Changes when |
|---|---|---|
| `docs/PLAN.md` (this) | what we are building, the decisions, the architecture | a decision is taken or reversed |
| `docs/ROADMAP.md` | the steps, in order, with status | a step starts or finishes |
| `docs/DEV_LOG.md` | where we are, next up, open items, a short dated log | every working session |

---

## 1. What we are building

A habit tracker for **self-directed learners and developers**. You set a learning goal ("Learn Rust
in 60 days"), attach the daily habits that get you there and the notes and links you study from, and
check in every day. Streaks, completion rates, levels and a dashboard of your patterns show how it is
going.

**Why someone would choose it over a generic tracker** (review A1, A6):
- goals, habits and study material in one place;
- **auto check-ins**: a habit linked to an outside service (e.g. GitHub — "pushed a commit today")
  is ticked by the app, so the streak follows real activity (Phase 11, waiting for a decision);
- forgiving but honest streaks: rest days with a weekly limit (§3.4).

**How it is used:** a web app installed as a PWA on the phone (home-screen icon, web push reminders),
built in the separate `habit-tracker-web` repo. This repo is the API.

**Plans:** Free — up to **7 active habits and 2 active goals**. Pro — no limits, plus the paid
features (integrations, file attachments, AI insights) once they exist. Billing comes only when there
are users asking for Pro.

---

## 2. Stack

Java 21 · Spring Boot 4.1 · Spring MVC (blocking, not WebFlux) · Spring Data JPA · PostgreSQL ·
Flyway · Spring Security with our own JWTs · springdoc (Swagger) · Lombok · Testcontainers.
One Maven module.

---

## 3. Decisions

Settled decisions, grouped by area. A change to one is written here first.

### 3.1 Architecture and code
- **Monolith, package-by-feature, one Maven module.** A feature uses another through its service,
  never its repository; repositories are package-private.
- **Numbers are computed on read, never stored**: streaks, stats, XP, goal progress, dashboard. No
  counter can drift. Cache only if a measurement asks for it.
- **Lombok** `@Getter`/`@Setter` on entities and request DTOs (never `@Data` on entities); response
  DTOs are records; `@RequiredArgsConstructor` injection; config through `@ConfigurationProperties`.
- **Flyway owns the schema** (`ddl-auto=validate`). Every change is a new `V{n}__….sql`; an applied
  one is never edited; a new enum value needs a migration that restates its check.

### 3.2 Security
- **Access token:** JWT, 15 min, `sub` = email, `uid` = user id, `Authorization: Bearer`.
- **Refresh token:** random, stored as SHA-256 only, 7 days, rotated on every use, in an httpOnly,
  Secure, `SameSite=Strict` cookie scoped to `/api/auth`. A *rotated* token used again revokes all of
  the user's tokens; one revoked by sign-out is just invalid.
- **Every query is scoped by the acting user**, taken from the security context. Someone else's data
  is **404**, never 403.
- **Passwords:** BCrypt, 8–72 bytes. Forgot → a 30-minute single-use link (1 a minute, 5 an hour per
  account); reset and change sign out every other session.
- **Google sign-in and existing accounts:** found by Google id first, then by email (and linked).
  Linking an account whose email was never verified first removes its password and sessions — whoever
  set them may not own the address (account pre-hijacking, review B2). Google must say the email is
  verified.
- **Rate limits** on the public auth endpoints, per IP, plus failed logins per email.
- **Never logged:** passwords, tokens, cookies, `Authorization`, email addresses, note text, file
  names. Logs carry ids. A masking layout is the backstop.
- **Secrets only from the environment**; the app refuses to start without `JWT_SECRET` (≥ 32 bytes).
- **Google sign-in (2.3):** ID-token flow — the browser gets a Google ID token, the API verifies it
  locally against Google's keys (`iss`, `aud` = our client id, `exp`, `email_verified`), then finds,
  links or creates the user and issues our tokens. `POST /api/auth/google`.

### 3.3 Habits, check-ins and streaks
- **Frequency:** `DAILY`, `SPECIFIC_DAYS` (chosen weekdays), `X_TIMES_PER_WEEK` (1–6, Mon–Sun weeks).
- **Kinds:** a *build* habit is done by checking in; a *quit* habit ("no sugar") is clean every day
  without a slip, and a check-in records a slip. Quit habits have no reminders and no rest days.
- **Check-in** sets the day's absolute count (`0` undoes), as one upsert on `(habit, day)`. Allowed:
  today and up to 7 days back, not before the habit existed. Archived habits can't be checked in.
- **A day is done** when its count reaches the target **that applied when it was logged** — raising a
  target later doesn't rewrite the past.
- **Streaks are strict**: a scheduled day that ends undone resets the run. Days for daily and weekday
  habits, weeks for N-a-week habits. Today (or this week) never breaks a run while still in progress.
- **Changing a habit's schedule starts a new streak**; the XP earned under the old schedule is kept.
- **"Today" is the user's calendar day**, from `User.timezone` (IANA). The web app detects the
  timezone at sign-up and offers to update it when the browser's differs.
- **Archive** is the normal way to stop a habit (history and XP stay). **Delete** removes the habit
  and its logs for good.

### 3.4 Rest days (decided 2026-10-10)
- Per habit, per day, for daily and chosen-weekday habits, on a scheduled day.
- A rest day **neither breaks nor extends** the streak and isn't expected in completion rates.
- Per habit per week (Mon–Sun): the **1st is free, the 2nd costs 100 XP, the 3rd 200 XP; at most 3**.
- **Level comes from all XP ever earned** and never drops. Rest days are paid from a **spendable
  balance** = earned − spent. Not enough balance → refused.
- Undoing a rest day, or checking in on it, refunds its cost.

### 3.5 Goals, resources, levels, dashboard
- **Goal:** a habit serves at most one goal, with a target number of done days; progress = average of
  its active habits' `min(done since linked / target, 1)`. Only ever goes up. The user marks a goal
  achieved or abandoned.
- **Resources:** notes (Markdown), links (http/https only, never fetched by the server), files. **File
  uploads are switched off** (`app.files.enabled=false`) until there is a host with lasting storage
  (Cloudflare R2's free tier is the plan); notes and links work.
- **XP:** +10 per counted done day, +5 more while the run is ≥ 7 days (1 week), +50/200/500/1500 at
  7/30/100/365 days (1/4/14/52 weeks), +500 per achieved goal. Level `n` starts at `50·(n−1)·n` XP.
  Tiers Bronze 1–4, Silver 5–9, Gold 10–19, Platinum 20–34, Diamond 35+.
- **Dashboard** (`GET /api/dashboard`): today's habits, 7/30/90-day completion with the change against
  the previous period, streaks at risk, best and slipping habits, goals, level. **Patterns**
  (`GET /api/dashboard/patterns`): year heatmap, weekday rates, check-in hours. Active habits only.

### 3.6 Notifications and email
- **Reminders:** a per-habit local time; a minute scheduler creates an in-app notification (unique per
  habit, day and type) and sends a **web push**. **Reminders are never emailed.**
- **Email is only for** password reset, email verification and promotional mail. Promotional mail
  only to users who opted in (`marketingEmails`, default off), sent from the email provider's own
  campaign tool.
- **Provider:** any SMTP service, set by environment variables. Start on **Brevo's free plan**
  (300 emails/day); set SPF, DKIM and DMARC once there is a domain.
- Sending happens after the transaction commits, off the request thread; a failed send is logged and
  dropped.

### 3.7 Logging
- One pattern for every line: `time [cid=…] level pid --- [thread] logger : message`. `cid` is the
  request's correlation id (`X-Correlation-Id`), also returned in error bodies.
- Async appenders that **block rather than drop** when full. Console in every environment; the
  rolling file only on a machine with a lasting disk.

---

## 4. Architecture

```
            ┌────────────────────────────┐
 browser ──▶│ habit-tracker-web (PWA)    │  same site as the API (cookie is SameSite=Strict)
            └─────────────┬──────────────┘
                          │ /api/**  (Bearer access token; refresh cookie on /api/auth)
            ┌─────────────▼──────────────────────────────────────────────┐
            │ Spring Boot API                                            │
            │  CorrelationIdFilter → rate limits → JWT filter → MVC      │
            │  controllers → services → repositories (JPA / native SQL)  │
            │  ReminderScheduler (every minute) → notifications, push    │
            │  after-commit listeners → email (SMTP), push, file cleanup │
            └──────┬───────────────────────┬──────────────────┬─────────┘
                   │                       │                  │
              PostgreSQL              SMTP provider      Web Push services
              (Flyway)                (Brevo)            (browser vendors)
```

**Packages** (`com.nayeem.habittracker`):

| Package | Owns |
|---|---|
| `common` | response envelope, `ErrorCode` + global handler, pagination, correlation id, log masking |
| `configs` | app properties, clock, JPA auditing, Swagger |
| `security` | security chain, JWT, rate limits |
| `user`, `auth` | users and profile; sign-up/in, refresh tokens, one-time tokens (reset, verify) |
| `account` | the account as a whole: delete, export |
| `habit`, `checkin` | habits and schedules; check-ins, streak and stats calculators |
| `goal`, `resource`, `file` | goals and progress; notes/links/files; file storage |
| `level`, `dashboard` | XP and levels; dashboard and patterns |
| `notification` | in-app notifications, reminder scheduler, email sender |
| `push` | web push subscriptions, RFC 8291 encryption, VAPID-signed sending |

**Request path:** the correlation id goes into the MDC first, so every line of a request — even a
401 — carries it. The JWT filter puts an `AuthUser` (ids only) in the security context; controllers
pass `user.id()` to services; services load data scoped by that id. Errors become the `HttpResponse`
envelope with an `ErrorCode`.

**Calculators are pure** (`StreakCalculator`, `StatsCalculator`, `XpCalculator`,
`GoalProgressCalculator`, dashboard calculators): no Spring, no database, "today" passed in. Services
load the logs, calculators do the maths, so every screen shows the same numbers.

**Deploy shape:** one API instance behind a TLS reverse proxy, managed PostgreSQL with backups,
config from environment variables (`application-prod.properties` for production defaults). The web
app and the API must be on the same site (`app.example.com` + `api.example.com`, or one domain with
`/api` proxied), or the refresh cookie is never sent.

---

## 5. Way of working

- **Small PRs**, one roadmap step each, under ~10–15 files. Each phase has a base branch; each step
  branches from the previous step and PRs into the base, in order; the base PRs into `staging`;
  `staging` → `main` is a release.
- Conventional commits (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`, `build:`). Commits
  carry Nayeem's identity only. No force-push.
- **Tests:** pure unit tests for calculators; integration tests on real PostgreSQL (Testcontainers,
  never H2) for ownership, upserts, auth flows and migrations. Docker must be running.
- The PR that finishes a step ticks it in `ROADMAP.md` and adds a short `DEV_LOG.md` entry.

---

## 6. Open questions

| # | Question | Needed by |
|---|---|---|
| 15 | Which integrations first for auto check-ins (recommended: GitHub by username, no OAuth)? | Phase 11 |
| 16 | Where to host (decides file storage and the deploy steps)? | D.3, file uploads back on |
