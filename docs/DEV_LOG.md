# DevHabit — Dev Log

Read first every session. Steps are in `docs/ROADMAP.md`, decisions in `docs/PLAN.md`.
Entries are short: what changed, what was decided, what bit us. Details live in the PRs.

## Where we are

- M1–M5 done and on `staging`: auth (incl. password reset/change), habits, check-ins, streaks,
  stats, reminders, notifications, goals, resources, levels, dashboard; Flyway; logging.
- The 2026-10-09 review (`docs/REVIEW-2026-10-09.md`) set the next phases: 6 clean-up and fixes,
  7 security and launch, 8 accounts, 9 habits, 10 web push.
- Frontend: `habit-tracker-web`, its own plan and log; built by another agent.

## Next up

Phase 6 → 10 in roadmap order. Then the decisions in `PLAN.md` §6 (integrations, host, Google id).

## Open items

| Item | Who | Blocks |
|---|---|---|
| Google OAuth client id | Nayeem | 2.3 |
| Choose a host | Nayeem | D.3, file uploads back on |
| Brevo account + SMTP key (free) | Nayeem | real email in production |
| Which integrations first | Nayeem | Phase 11 |

## How to run

- PostgreSQL running; a git-ignored `.env` in the project root with `DB_USERNAME`, `DB_PASSWORD`,
  `JWT_SECRET` (≥ 32 bytes), optional `DB_URL`. Real environment variables override it.
- Tests need Docker (Testcontainers). After switching between far-apart branches: `./mvnw clean test`.
- Local email: Mailpit in Docker with `MAIL_SMTP_AUTH=false`.
- Real email, free (Brevo, 300/day): sign up at brevo.com → add and verify the sender address →
  SMTP & API → create an SMTP key. Then set `APP_NOTIFICATIONS_EMAIL_ENABLED=true`,
  `SPRING_MAIL_HOST=smtp-relay.brevo.com`, `SPRING_MAIL_PORT=587`, `SPRING_MAIL_USERNAME=<Brevo SMTP
  login>`, `SPRING_MAIL_PASSWORD=<SMTP key>`, `APP_NOTIFICATIONS_EMAIL_FROM=<verified sender>`. Once
  there is a domain, authenticate it in Brevo (SPF, DKIM, DMARC) so mail doesn't land in spam.

## Gotchas worth remembering

- `ddl-auto=update` never drops columns or widens enum checks: it caused the 2026-10-02 sign-up
  failure and the 2026-10-07 file upload 500. Flyway (D.1) ended that class of bug.
- Only a SQLSTATE 23505 means "email taken"; other integrity errors are real faults.
- Jackson 3 writes `HttpStatus` as `"404 NOT_FOUND"`; the envelope stores the enum name.
- SMTP with auth on and no username fails every send (`MAIL_SMTP_AUTH=false` for Mailpit).
- Refresh-token reuse is only a *rotated* token presented again; otherwise a password change signed
  out the session it was made in.

---

## Log

**2026-10-10** — 9.1: optional `unit` (≤ 20, V11) on habits, in habit responses and on the
dashboard's today list.

**2026-10-10** — 8.5: `DELETE /api/habits/{id}` → 204; its logs and notifications go by cascade, and
with them the XP and goal progress they earned (archive keeps them). A JPQL bulk delete, so no loaded
log can still reference the habit at flush. Phase 8 done except 2.3 (waits for the client id).

**2026-10-10** — 8.4: `GET /api/me/export` → `devhabit-export.json` (attachment, `no-store`): profile,
habits incl. archived, every check-in with the target it was judged by, goals, resources (file
entries, not bytes), in one read-only transaction. Each feature exposes an `exportAll(userId)`.

**2026-10-10** — 8.3: `DELETE /api/me` (password when the account has one; same 5-miss limit as
change password, now shared as `AuthService.confirmPassword`). V10 puts `on delete cascade` on every
foreign key to `users`, and on logs/notifications to `habits` (for 8.5). File bytes go after commit.
New `account` package orchestrates (it needs user, auth and file).

**2026-10-10** — 8.2: `PUT /api/me` replaces name, timezone (region names only) and
`marketingEmails` (V9, default off). The web app detects the browser's timezone and offers to switch
when it differs from the profile's.

**2026-10-10** — 8.1b: `users.email_verified_at` (V8). Sign-up emails a 24-hour link
(`<frontend>/verify-email#token=…`); `POST /api/auth/email/verify` (public) confirms it,
`POST /api/auth/email/verification` (signed in) sends another (409 when already confirmed). A password
reset also confirms the address. `emailVerified` on the user. Nothing is blocked for unverified users
yet; Google linking (2.3) and promotional email will require it. Found: a used one-time token stayed
usable within the same persistence context (bulk update only) — the token is now marked used itself.

**2026-10-10** — 8.1a: `password_reset_tokens` → `one_time_tokens` with a `purpose`
(`PASSWORD_RESET`, `EMAIL_VERIFICATION`; V7). `OneTimeTokenService` issues (1 a minute, N an hour),
consumes (purpose must match) and retires; `PasswordResetService` keeps only the reset email.

**2026-10-10** — 7.6: `Dockerfile` (JDK build → JRE run, non-root, `prod` profile), `.dockerignore`,
`.env.example` with every variable, GitHub Actions CI (`./mvnw -B verify` on PRs and on pushes to
`staging`/`main`). Smoke-ran the image against Postgres; it found two start-up crashes the tests
couldn't (they run with a writable home): logback opened the log file even when the prod profile
didn't use it, and local file storage created its folder even with uploads off. Both fixed. Phase 7
done.

**2026-10-10** — 7.5: `app.files.enabled` (`APP_FILES_ENABLED`, default false). Off, `FileService.store`
refuses with 403 `FILE_UPLOADS_DISABLED`; downloading and deleting stored files still work. The
frontend hides its upload button behind its own flag.

**2026-10-10** — 7.4: email is sent `@Async` after commit (Boot's executor; a `TaskDecorator`
carries the MDC so the line keeps its `cid`). Reminders create the in-app notification only — no
email; web push comes in Phase 10. Brevo free SMTP set-up written under "How to run".

**2026-10-10** — 7.3: confirmed a password over 72 bytes answered 500 (Spring Security's BCrypt
throws). `@MaxBytes(72)` on every password field → 400 `VALIDATION_FAILED`; min stays 8 characters.

**2026-10-10** — 7.2: in-memory fixed-window `RateLimiter` (one instance; Redis if we ever run
several). `AuthRateLimitFilter` per IP: login 10/min, register 10/h, refresh 30/min, forgot 5/h,
reset 10/h → 429 `RATE_LIMITED` + `Retry-After`. Five wrong passwords lock that email (or a wrong
current password, that account's change) for 15 minutes. Tests switch the IP filter off (one shared
client IP) and unit-test it instead.

**2026-10-10** — 7.1: `application-prod.properties` (`SPRING_PROFILES_ACTIVE=prod`): INFO logs, no SQL,
no Swagger or API docs, actuator health/info only, forwarded headers trusted, `CORS_ALLOWED_ORIGINS`
and `APP_FRONTEND_URL` required; logs to stdout only under `prod`. Nightly job deletes refresh tokens
and reset links expired over a day ago. A test boots the prod profile.

**2026-10-10** — 6.5: reminders run one transaction per habit (a failure is logged, the rest still
go out); `RefreshTokenService` uses the injected clock; mark-all-read is one `update`. Found while
there: PostgreSQL reads an offset like `+06:00` as UTC−6 in `at time zone`, so sign-up now accepts
region names only (`ZoneId.getAvailableZoneIds()`). 293 tests. Phase 6 done.

**2026-10-10** — 6.4: changing frequency type or days keeps the old schedule in
`habits.past_schedules` (jsonb, V6) and starts the new one today (`schedule_since`). Streaks, stats
and the dashboard start at `Habit.startDay`; XP adds each past schedule's own walk, so nothing earned
is lost. Patterns (heatmap, weekdays) and the check-in date rule still use the creation day.

**2026-10-10** — 6.3: `habit_logs.target_count` (V5, backfilled with today's targets) is written
with every check-in; "done" compares against it everywhere (streaks, stats, XP, goals, reminders).
Raising a target no longer un-does past days. 290 tests.

**2026-10-10** — 6.2 logging: OpenTelemetry starter, BOMs and MDC appender removed (exporters were
off; the correlation id already ties a request's lines together), lines now show `[cid=…]`. Async
appenders kept but block when full instead of dropping. Unused OAuth2 client starters and
`app.backendUrlShort` removed (Google sign-in will use the JOSE module only).

**2026-10-10** — Docs rewritten (6.1): plan is now overview + decisions + architecture (entity
design removed — the code and migrations are the source); roadmap and this log compacted. Decided:
rest days (1 free a week, then 100 / 200 XP, max 3; level from lifetime XP, a spendable balance pays),
file uploads off until a host with lasting storage, reminders by web push not email, email only for
reset / verification / promotions (Brevo free), free plan 7 habits / 2 goals, backend only here —
the frontend agent builds PWA and screens.

**2026-10-09** — Product and code review written (`docs/REVIEW-2026-10-09.md`).

**2026-10-08** — D.1b logging: OpenTelemetry trace ids, masking layout, async appenders, retention.

**2026-10-07** — D.1 Flyway (`V1` from Hibernate's schema, `V2` heals stale enum checks, enum drift
test). 2.4a forgot/reset password; 2.4b change password; fixed "sign out everywhere also signed out
here" (`rotated_at`, V4). Google sign-in moved to the ID-token flow.

**2026-10-06** — M4 levels (X.1–X.2), M5 dashboard (A.1–A.3), R.3b–R.3c file upload/download.

**2026-10-05** — R.3 file design (local disk behind `FileStorage`, 10 MB / 100 MB, type from bytes).
R.3a storage.

**2026-10-03 – 10-04** — M2 goals (G.1–G.4), M3 notes and links (R.1–R.2).

**2026-10-01 – 10-02** — Phase 5 reminders and email (M1 complete); frontend F.1–F.6 started in its
own repo; fixed sign-up failing on stale columns.

**2026-09-30** — Phases 0–4 in one push: conventions and docs, error envelope, Testcontainers, JWT
security, register/login/refresh/logout, habits, check-ins, streaks, stats. Decided: package-by-
feature, `sub` = email, the user's timezone defines "today", calendar-week N-a-week streaks.

**2026-09-11 – 09-19** — Project set up; Swagger and logging config; `feat/user-service` draft
(never merged, lifted into Phases 0–2).
