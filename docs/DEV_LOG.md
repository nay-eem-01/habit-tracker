# DevHabit — Development Log

Read this first at the start of every working session. Newest entry on top.
The step-by-step plan and overall progress are in `docs/ROADMAP.md`; decisions are in
`docs/PLAN.md`.

---

## Where we are

- **Phases 0–5 done** (2.3 Google sign-in deferred) — 25 of 26 steps, all on `staging`. **M1 is
  feature-complete.** The core loop works: account → habits → daily check-ins → strict streaks →
  7/30-day stats → reminders (in-app, optional email), all in the user's timezone.
- **M2 Goals is done and merged.** **M3 Resources is done**: notes and links (R.1, R.2) and file
  uploads (R.3a–R.3c, `PLAN.md` §13) — R.3a/R.3b on `staging` (#51–#54), R.3c on
  `feat/resource-file-download`. R.3d (S3 storage) waits for the deploy steps. **Next: M4 Levels**
  (X.1 XP/level/tier calculator, `PLAN.md` §11.3; Q8 to confirm first).
- The frontend is built by another agent in its own repo (`habit-tracker-web`); backend work follows
  this roadmap.
- Branch flow: step branch from the previous step's branch → PR into the phase's base branch →
  base PRs into `staging`. Claude commits and pushes and gives PR links (no `gh` on the machine);
  Nayeem opens and merges. Commits carry Nayeem's name only.
- Tests need Docker running (Testcontainers). 194 tests pass.
- To run locally: PostgreSQL running, and a git-ignored `.env` in the project root with `DB_URL`
  (optional), `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (≥ 32 bytes) — the app reads it itself
  (`spring.config.import`); real environment variables override it, and the old `db_user_name` /
  `db_password` names still work. Uploaded files go to `~/.habit-tracker/files` unless
  `APP_FILES_DIR` says otherwise.

## Next up

1. M4 Levels → M5 Dashboard → M6 AI (`PLAN.md` §11). The frontend has no file upload UI yet.
2. Before any shared deploy: Flyway (D.1), production profile (D.2).
3. Later: 2.3 Google sign-in.

## Open items

| Item | Needs | Blocks |
|---|---|---|
| Google OAuth client (id + secret, redirect URI) from Google Cloud Console | Nayeem, later | 2.3 (deferred) |
| Set `JWT_SECRET` (≥ 32 bytes) in the run configuration — the app no longer starts without it | Nayeem | running locally |
| Remote branch `docs/implementation-plan-v3` (2026-09-25, unmerged) is superseded by `PLAN.md` and today's decisions, and contradicts them in places (defers `X_TIMES_PER_WEEK`, manual testing only, 403 for others' habits). Delete it, or lift anything useful (e.g. its `DateResolver` idea) first | Nayeem | nothing |
| Local branch `feat/user-service` — fully lifted into PRs #4–#11; safe to delete (`git branch -D`) | Nayeem | nothing |
| PR #2's commits are authored as `Claude <noreply@anthropic.com>`; from now on commits carry Nayeem's identity. Rewriting merged history is not worth it | — | nothing |
| Week starts on Monday for every user (`PLAN.md` §8b) — per-user week start if anyone asks | later | nothing |

---

## 2026-10-06 (roadmap R.3c — download and delete files; M3 done)

**Done** (branch `feat/resource-file-download` → `feat/files-base`)
- `GET /api/resources/{id}/file` — owner only (someone else's → 404 `RESOURCE_NOT_FOUND`), a note
  or link → 404 `FILE_NOT_FOUND`. Streams the bytes with the stored type, `Content-Length`,
  `Content-Disposition: attachment` (`filename*=UTF-8''…`, so any name survives),
  `X-Content-Type-Options: nosniff`, `Cache-Control: no-store, private`.
- Bytes gone from storage but row still there → 404 `FILE_NOT_FOUND` and an ERROR log with the file
  id and key (not a 500).
- Deleting a `FILE` resource deletes its `stored_files` row in the same transaction and the bytes
  after commit (`FileService.delete`); a rolled-back delete keeps both. Fixes the R.3b gap that
  reached `staging` with #54.
- `ResourceFileDownloadIntegrationTest` (5), one more case in `FileServiceIntegrationTest`.
  194 tests pass.

**For the frontend**
- Download with `fetch` + the bearer token, then save or preview the blob — an `<a href>` can't send
  the header. Check `file.size` ≤ 10 MB before uploading (see R.3b).

## 2026-10-06 (roadmap R.3b — upload a file as a resource)

**Done** (branch `feat/resource-file-upload` → `feat/files-base`)
- `ResourceType.FILE`; `Resource.file` (one-to-one `StoredFile`, `file_id` unique, never swapped).
- `POST /api/resources/files` — multipart: `file` part + `title`, `body`, `goalId`, `pinned` form
  fields (`ResourceFileRequest`). 201 + `Location`. The goal is checked before the upload is stored,
  so a 404 writes no bytes; one transaction with `FileService.store`.
- Responses carry `file: {name, contentType, sizeBytes}` (null for notes and links); lists fetch it
  in the same query (`@EntityGraph` on `findAll(spec, pageable)`).
- JSON `POST /api/resources` with `FILE` → 400 `RESOURCE_INVALID`. `PUT` on a file changes title,
  body, goal and pin only; the type can't change either way; a file has no `url`.
- `GlobalExceptionHandler`: missing multipart part / broken multipart → 400 `MALFORMED_REQUEST`
  (were 500s); Spring's upload limit → 413 `FILE_TOO_LARGE`.
- `server.tomcat.max-swallow-size=20MB`: checked with a real server — with Tomcat's default 2 MB a
  12 MB upload had its connection cut mid-response instead of getting the 413. Now a clean 413 up to
  ~20 MB; beyond that Tomcat still drops the connection (on purpose). The frontend should check
  `file.size` before sending.
- `ResourceFileUploadIntegrationTest` (7), 2 cases in `GlobalExceptionHandlerTest`. 188 tests pass.

**Not done yet (R.3c)**
- Deleting a `FILE` resource leaves its `stored_files` row and bytes behind; R.3c removes them after
  commit. `feat/files-base` isn't merged to `staging` until then.

## 2026-10-05 (roadmap R.3a — file storage)

**Done** (branch `feat/file-storage` → `feat/files-base`)
- New `file` package. `StoredFile extends AuditModel` (`stored_files`): `user`, `storageKey` (random
  UUID, unique), `originalName` (cleaned: last path segment, no control characters, ≤ 255 keeping the
  extension), `contentType` (detected), `sizeBytes`, `sha256`.
- `FileStorage` (`put` / `open` / `delete`) with `LocalDiskFileStorage`: `app.files.dir`
  (`APP_FILES_DIR`, default `~/.habit-tracker/files`), temp file + atomic move so a failed write
  leaves nothing; keys must be `[A-Za-z0-9-]`, so no path can escape the directory.
- `FileType` — the allowlist (PNG, JPEG, WebP, GIF, PDF, text `.txt`/`.md`). Type detected from
  the first 64 KB with Tika core (`tika-core` 4.1.0, magic bytes only); the extension must match.
  HTML, SVG and XML named `.txt` or `.png` are refused.
- `FileService.store(userId, upload)` — empty → 400 `FILE_EMPTY`, > 10 MB → 413 `FILE_TOO_LARGE`,
  type → 415 `FILE_TYPE_NOT_ALLOWED`, over 100 MB per user → 413 `FILE_QUOTA_EXCEEDED`; all checked
  before a byte is written. Bytes then row; a rollback (also the caller's, R.3b) deletes the bytes.
  Logs id, size and type — never the name.
- `app.files.max-file-size` / `user-quota`; multipart limits 10 MB / 11 MB.
- `FileTypeTest` (6), `LocalDiskFileStorageTest` (3), `FileServiceTest` (2),
  `FileServiceIntegrationTest` (4). 179 tests pass.

**Moved**
- The quota check came into R.3a (it belongs in `store`); R.3b keeps the endpoint and maps
  Spring's multipart-size error to 413.

**Known limit**
- Two uploads at the same moment can both pass the quota check and go slightly over. Fine at one
  user's scale.

## 2026-10-05 (R.3 file uploads — design draft)

**Done**
- `PLAN.md` §13: what taskatask-backend's `FileObject` module does, what to copy (file metadata
  apart from the feature entity, type detected from bytes, random keys, attachment downloads) and
  what not to (no owner on files, orphans from two-step upload, no type allowlist, 256 MB in
  memory, public caching of private files).
- Options and recommendations for Q12 (storage: local disk behind `FileStorage` now, S3-compatible
  later), Q13 (10 MB / file, 100 MB / user; images, PDF, text), Q14 (one multipart call creates the
  resource). Steps R.3a–R.3d proposed.

**Decided**
- Q12–Q14 answered with the recommendations (Nayeem, 2026-10-05).

**To know**
- taskatask-backend has AWS keys committed in its `application-development` / `-staging`
  properties — flagged to Nayeem, to rotate there.

## 2026-10-02 (frontend G.0 — test pass)

**Done** (details in `habit-tracker-web/docs/DEV_LOG.md`)
- Drove the real frontend against this backend and local Postgres, desktop and phone: sign-up,
  sign-in, habits, check-ins, streak/stats/logs, notifications, refresh cookie, sign-out all work.
  The API contract the frontend relies on held up; **no backend changes needed**. Two phone layout
  bugs were found and fixed on the frontend side only.

## 2026-10-02 (sign-up bug found while testing the frontend; frontend planning moves)

**Found**
- Every sign-up answered "email already exists". Cause: the local `users` table still had
  `user_email` / `user_name` / `user_password` from the old `feat/user-service` draft (`ddl-auto=update`
  adds columns, never drops them). `user_email` is NOT NULL, so each insert failed, and
  `UserService.createLocalUser` turned *any* `DataIntegrityViolationException` into `USER_EMAIL_TAKEN`.
  Nayeem dropped the three stale columns by hand. Flyway (D.1) removes this class of problem.

**Done**
- `createLocalUser` now reports `USER_EMAIL_TAKEN` only for a unique violation (SQLSTATE 23505);
  any other integrity error is rethrown as the real fault. Three unit tests (no database).

**Decided**
- **Frontend planning lives in the frontend repo** (`habit-tracker-web/docs/`), not here: its plan,
  roadmap and design decisions are there. This log only records that frontend work happened and what
  it needed from the backend. Phase F in `ROADMAP.md` stays as the summary of F.1–F.6.

## 2026-10-02 (frontend F.5 and F.6 — habit detail, notification bell)

**Done** (in `habit-tracker_web`: `feat/habit-detail` from `staging`, then `feat/notifications` on top of it)
- F.5: `/habits/:id` — name, category, schedule, reminder, Edit link; current and longest streak (days,
  or weeks for N-a-week); 7- and 30-day completion ("6 of 7 done", or "Nothing was due yet" when the
  API's `rate` is null); log history for the last 90 days, newest first, paged, counts for habits with
  a daily target. Habit names on the list and Today now link to it.
- F.6: a bell in the header polls `/api/notifications/unread-count` every 30 s (only while the tab is
  visible) and shows the count; opening it lists the latest 10, unread marked; clicking one marks it
  read and opens its habit; **Mark all read**; closes on Escape or an outside click. The reminder-time
  field was already in the habit form (F.3), so nothing more was needed there.
- 79 frontend tests, typecheck, lint and `vite build` pass. **Not yet run against the live backend** —
  that is the testing pass Nayeem wants next.

**Decided**
- No separate notifications page yet: the panel shows the latest 10. Add paging if it is ever too few.

## 2026-10-02 (frontend F.4 — Today)

**Done** (in `habit-tracker_web`, branch `feat/today-view` on top of `feat/habits-page`)
- `/` is now Today: the date in the user's timezone, "N of M done" with a row of squares, **To do**
  and **Done** lists. Daily and chosen-weekday habits show on their days (weekday habits that
  aren't scheduled are hidden); N-times-a-week habits are open all week with "1 of 3 this week".
- One tap checks in (an absolute `completedCount`); a habit with a daily target gets **+1** and
  Undo (take one away). Taps show at once and are put back with a message if the server refuses.
  The streak (days, or weeks for N-a-week) sits beside each habit and refreshes after a check-in.
- Header got Today / Habits links; phone width checked (name hidden under 640 px).
- 62 frontend tests, and a real-browser run against this backend (register → add habits → check in
  → +1/undo → reload → phone width): works, no app errors.

**Worth a backend step later**
- Today costs two requests per habit (this week's logs + the streak) because there is no
  "today for all habits" endpoint. Fine for tens of habits. A `GET /api/today` (or the M5 dashboard
  call) would make it one request — add when the dashboard milestone starts, or sooner if it feels slow.

## 2026-10-02 (frontend F.3 — habits)

**Done** (in `habit-tracker-web`, branch `feat/habits-page` on top of `feat/auth-pages`)
- `/habits`: Active / Archived tabs, each habit with its schedule, target and reminder in words,
  Edit, Archive (with an Undo line) / Restore, pagination. `/habits/new` and `/habits/:id/edit`
  share one form: name, category, how often (every day / chosen weekdays / N times a week), times a
  day, optional reminder time (shows the user's timezone). Edit is the full-replace PUT, so turning
  the reminder off clears it. `/` redirects to `/habits` until the Today view (F.4).
- API types generated from this backend's OpenAPI spec (`npm run gen:api`) for request bodies.
- 42 frontend tests; also run end to end in a real browser (Playwright + Chrome) against this
  backend on a throwaway Postgres: register → create three kinds of habit → edit → archive/undo →
  reload keeps the session → sign out. No app errors.

**Found while testing for real**
- The browser sends `Origin: http://localhost:<dev port>` through the Vite proxy; the backend only
  allows `CORS_ALLOWED_ORIGINS` (default `http://localhost:3000`) and answered **403 "Invalid CORS
  request"** to every POST on any other port. Fixed on the frontend side: the dev proxy drops the
  `Origin` header (same as production, where app and API share one origin). Nothing to change here.
- The OpenAPI spec types every response `payload` as `unknown` (`HttpResponse.payload` is
  `Object`), so generated types cover requests only and response types are hand-written. Worth
  fixing later (e.g. springdoc response schemas per endpoint) — not urgent.

## 2026-10-01 (frontend F.2 — sign in, register, protected routes)

**Done** (in `habit-tracker-web`, branch `feat/auth-pages` on top of `feat/scaffold`)
- `AuthProvider` + guards: on start it signs back in from the httpOnly refresh cookie; `RequireAuth`
  sends anonymous visitors to `/signin` and brings them back after; `GuestOnly` keeps signed-in
  users off sign-in/register; a lost session (refresh fails) clears the query cache and signs out.
- Sign-in and register pages: plain-language errors keyed on `errorCode`, per-field server messages
  next to the field, the browser's timezone sent at sign-up (check-ins and reminders follow it).
- Look: split screen — a deep-blue panel with the streak chain grid (one ember "today" cell, one
  fill animation, off for reduced motion) beside a left-aligned form. Bricolage Grotesque +
  Instrument Sans, self-hosted via fontsource.
- 21 tests (client 12, app flows 9: guards, sign-in, register errors, sign-out). Build and lint clean.

**To know**
- Port 8080 on Nayeem's machine currently answers as another project (`AgriculturalBlogApplication`),
  so the dev proxy would hit the wrong backend — stop it before `npm run dev` against this API.
- API types are still hand-written (auth only); `npm run gen:api` needs this backend running.

## 2026-10-01 (Phase 5 merged, frontend started)

**Done**
- Phase 5 merged to `staging` (PRs #28–#32). Phases 0–5 are all on `staging`.
- Phase F (frontend) added to the roadmap; `habit-tracker-web` is its own repo, F.1 scaffold in
  progress there.

## 2026-10-01 (roadmap 5.4 — Phase 5 done, M1 feature-complete)

**Decided**
- Q11 answered with its recommended default (Nayeem asked to finish the phase without answering it):
  in-app always, email on top over SMTP, **off until configured**; push waits for the frontend.
  Change it if you want something else.

**Done**
- `NotificationSender` interface (`OutgoingNotification`: user id, address, title, body).
  `ConsoleNotificationSender` (default — logs the user id only) and `EmailNotificationSender`
  (`JavaMailSender`, plain text) chosen by `app.notifications.email.enabled`. Enabled without
  `spring.mail.host` the app refuses to start (tested).
- `ReminderService` publishes the event only for a *new* reminder; `NotificationDispatcher` sends
  it `AFTER_COMMIT` — a slow mail server never holds the transaction, a rolled-back run sends
  nothing, a duplicate minute sends nothing. A failing channel is logged (user id + exception class,
  never the address) and dropped; the in-app notification is already there.
- `spring-boot-starter-mail`; settings documented in `application.properties`
  (`APP_NOTIFICATIONS_EMAIL_ENABLED`, `…_FROM`, `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD`).
- `NotificationDeliveryTest` (5), `ReminderDeliveryIntegrationTest` (1, real commit, mocked
  sender). 132 tests pass.

**Not done / to know**
- No per-user email opt-out yet — it is a global switch. Add one before turning email on for
  strangers.
- Sending runs on the scheduler thread, one mail at a time; fine for one user's scale.

## 2026-10-01 (roadmap 5.3)

**Done**
- `ReminderScheduler` — `@Scheduled` every minute on the minute; calls `ReminderService.sendDue(now)`;
  a failed run is logged, the next minute carries on. Off with `app.reminders.enabled=false` (the
  integration-test base sets it, so tests drive `ReminderService` with a chosen instant).
- `HabitRepository.findRemindableAt(now)` — native SQL: active habits whose `reminder_time` equals
  the current minute **in the owner's timezone** (`at time zone users.timezone`) and that aren't
  done on the owner's today. One query for every user.
- `ReminderRules.isDue` (pure): `DAILY` always; `SPECIFIC_DAYS` on its weekdays; `X_TIMES_PER_WEEK`
  until the Mon–Sun quota is met (`HabitProgressService.doneDays`).
- `NotificationRepository.insertIfAbsent` — `INSERT … ON CONFLICT (habit_id, for_date, type) DO
  NOTHING`, so a restart or a second instance can't duplicate; returns 1 only for a new reminder
  (5.4 will send email only then). Minutes missed while the app is down are not made up.
- `ReminderRulesTest` (3), `ReminderServiceIntegrationTest` (6: local-minute match, no duplicates,
  archived / no reminder skipped, done today skipped, weekdays, N-a-week). 126 tests pass.

**Known limit**
- A user timezone Postgres doesn't know (Java accepts a few it lacks) would fail that minute's
  query for everyone. Unlikely with real IANA ids; revisit if it ever shows up in the logs.

## 2026-10-01 (roadmap 5.2)

**Done**
- New `notification` package. `Notification extends AuditModel`: `user`, `type`
  (`HABIT_REMINDER` for now), `title`, `body`, optional `habit`, `forDate` (the user's day it is
  for), `readAt` (null = unread). Unique `(habit_id, for_date, type)` is in place for 5.3's
  no-duplicates rule.
- `GET /api/notifications` (`page`, `size`; unread first, then newest), `GET
  /api/notifications/unread-count` (cheap to poll — added beyond the plan, the bell needs it),
  `POST /api/notifications/{id}/read` (idempotent, keeps the first read time),
  `POST /api/notifications/read-all`. Someone else's → 404 `NOTIFICATION_NOT_FOUND`.
- `PageRequests.unsorted` for queries that bring their own ORDER BY.
- `NotificationApiIntegrationTest` (5). 117 tests pass.

## 2026-10-01 (roadmap 5.1)

**Done**
- `Habit.reminderTime` (`LocalTime`, nullable = no reminder), the owner's local time of day; part of
  `HabitRequest` / `HabitResponse`, so create and the full-replace PUT set or clear it (omitted =
  cleared, like `category`).
- Wire format is strictly `HH:mm` (`"07:30"`); `25:00`, `7:3`, `07:30:15`, `noon` → 400.
- `HabitReminderTimeIntegrationTest` (2). 112 tests pass.
- Branches: `feat/reminders-base` (from `staging`) → `feat/reminder-time`.

**Next:** 5.2 notifications table and API.

## 2026-09-30 (roadmap 4.5 — Phase 4 done, the core loop works)

**Done**
- `GET /api/habits/{id}/stats` → `last7Days` / `last30Days`: `{days, done, expected, rate}`.
  `StatsCalculator` (pure): window clipped to the habit's first day; today counted only once done;
  weekday habits count scheduled days only; N-per-week expects `N × days / 7`, rate capped at 1;
  `rate` null while nothing was expected yet.
- Corrected `PLAN.md` §12.1: check-ins on unscheduled days count for neither streak nor rate (they
  would push a rate past 100 %); they stay in the logs.
- `StatsCalculatorTest` (8), stats case in `StreakApiIntegrationTest`. 110 tests pass.

## 2026-09-30 (roadmap 4.4)

**Done**
- `GET /api/habits/{id}/streak` → `{current, longest, unit}`; start = the habit's creation day and
  today = the user's today, both in their timezone. `HabitProgressService` holds the read-side
  numbers (stats join it in 4.5).
- `HabitLogRepository.findDoneDays` — days whose count reached the habit's target.
- `StreakApiIntegrationTest` (2). 101 tests pass.

**Known behaviour**
- "Done" uses the habit's *current* `targetCount`, so raising the target later re-judges past days.
  Fine for M1; a per-log target snapshot would fix it if it ever matters.

## 2026-09-30 (roadmap 4.3)

**Done**
- `StreakCalculator.calculate(type, config, doneDays, start, today)` → `Streak(current, longest,
  unit)`. Pure Java, "today" passed in.
  - `DAILY` / `SPECIFIC_DAYS` (`DAYS`): walks from the habit's first day to today over scheduled
    days; an undone scheduled day resets the run; today undone doesn't; unscheduled days (and
    check-ins on them) are skipped.
  - `X_TIMES_PER_WEEK` (`WEEKS`): Monday–Sunday weeks with ≥ N done days; the current week and the
    habit's first week never break the run; extra days in a week don't count twice.
- `StreakCalculatorTest` (16, nested per frequency type, incl. month/year boundary). 99 tests pass.

## 2026-09-30 (roadmap 4.2)

**Done**
- `GET /api/habits/{id}/logs?from&to&page&size` — inclusive dates, newest first; default the last
  30 days up to the user's today; `from` after `to` or a span over 366 days → 400. Archived habits'
  logs stay readable; someone else's → 404.
- `HabitLogsIntegrationTest` (3). 83 tests pass.

## 2026-09-30 (roadmap 4.1)

**Done**
- New `checkin` package. `HabitLog extends AuditModel`: `habit`, `logDate` (user's calendar day),
  `completedCount`, `note`; unique `(habit_id, log_date)`; `isDone()` = count ≥ the habit's target.
- `POST /api/habits/{id}/checkin` — body optional (empty = "mark today done"); `completedCount` is
  the day's **absolute** total (retry-safe, 0 undoes); note omitted = kept, `""` = cleared.
- One-statement upsert (`INSERT … ON CONFLICT … DO UPDATE … RETURNING id`); audit columns passed in
  because native SQL skips JPA auditing.
- Date rules (`PLAN.md` §12.1): not future, ≤ 7 days back, not before the habit existed →
  400 `LOG_DATE_OUT_OF_RANGE`; archived habit → 409 `HABIT_ARCHIVED`; someone else's → 404.
- `HabitService.getOwnedHabit` (public) for other features; `ClockConfig` — one injectable UTC
  `Clock`.
- `CheckInDateRulesTest` (4), `CheckInIntegrationTest` (6), `CheckInConcurrencyIntegrationTest`
  (8 threads → 1 row; stable over 4 runs). 80 tests pass.

## 2026-09-30 (roadmap 4.0 — check-in rules, reminders into M1)

**Decided** (`PLAN.md` §12)
- Check-in sets the day's absolute count (retry-safe), one-statement upsert; dates: not future,
  not before the habit existed, at most 7 days back; archived habits can't be checked in.
- Streak/stats details: unscheduled check-ins count for stats, not streaks; the day/week in progress
  and the habit's first (partial) week never break a streak.
- **Reminders and notifications move into M1** as Phase 5 (Nayeem, 2026-09-30): per-habit reminder
  time in the user's timezone, a minute scheduler, in-app notifications, then email. Push waits for
  a frontend. Q11 (channels / email provider) before 5.4.
- Roadmap is now 26 steps.

## 2026-09-30 (roadmap 3.3 — Phase 3 done)

**Done**
- `PUT /api/habits/{id}` — full replace with the same `HabitRequest` and the same schedule rules
  (omitted `category`/`targetCount` go back to empty / 1).
- `POST /api/habits/{id}/archive` and `/unarchive` — the only delete; idempotent; archived habits
  leave the default list and show under `?archived=true`. Logged with habit and user id.
- `HabitUpdateArchiveIntegrationTest` (4), incl. **every habit endpoint with another user's token →
  404, and the owner's habit unchanged**. 69 tests pass.

## 2026-09-30 (roadmap 3.2)

**Done**
- `common/response/PageResponse` — our own page record (`content`, `totalElements`, `totalPages`,
  `number`, `size`) instead of serializing Spring's `Page`.
- `common/pagination/PageRequests` — `size` clamped 1–100, `page` ≥ 0, `sortBy` must be on the
  endpoint's allowlist (400 otherwise), `id` as tie-breaker so pages are stable.
- `POST /api/habits` (201 + `Location`), `GET /api/habits/{id}`, `GET /api/habits`
  (`?archived=false` default, `page`, `size`, `sortBy` = `createdAt`|`name`, `sortDir`).
  One `HabitRequest` for create and (3.3) replace. Someone else's habit → 404 `HABIT_NOT_FOUND`.
- Test base gained `bearerFor(email)` (creates a user, returns the header value).
- `PageRequestsTest` (3), `HabitApiIntegrationTest` (7). 65 tests pass.

## 2026-09-30 (roadmap 3.1)

**Done**
- New `habit` package. `FrequencyType` `DAILY` / `SPECIFIC_DAYS` / `X_TIMES_PER_WEEK`
  (`PLAN.md` §8b). `FrequencyConfig` record (`days` as `DayOfWeek`s, `timesPerWeek` 1–6) stored as
  `jsonb` through Hibernate 7's own JSON mapping — no hypersistence-utils needed.
- `Habit extends AuditModel`: `user` (lazy, indexed, not updatable), `name`, `category`,
  `frequencyType` + `frequencyConfig` (set only together via `schedule(type, config)`, which
  rejects a mismatch with 400 `HABIT_INVALID_FREQUENCY` and drops fields that don't belong),
  `targetCount` (default 1), `archived` (renamed from the plan's `isArchived`).
- No `User.habits` collection — queries do that job, and it keeps `User` light.
- `HabitRepository` (package-private): `findByIdAndUserId`, `findAllByUserIdAndArchived` — every
  query carries the owner.
- `FrequencyConfigTest` (9), `HabitRepositoryIntegrationTest` (2, incl. `pg_typeof` = `jsonb`).
  55 tests pass.

## 2026-09-30 (product plan after M1)

**Decided**
- Core habit tracker (M1, Phases 3–4) first. Then M2 Goals → M3 Resources → M4 Levels →
  M5 Dashboard → M6 AI insights, designed in `PLAN.md` §11 and split into steps in `ROADMAP.md`.
- Goal progress, XP and levels are computed on read from habit logs, like streaks — nothing stored
  that can drift.
- Open questions Q6–Q10 (`PLAN.md` §11.6) each have a recommendation; they're confirmed before
  the milestone that needs them, not now.
- From now on Claude opens the PRs too (Nayeem merges) — needs `gh` installed and logged in.
- `.mcp.json` git-ignored (machine-specific IntelliJ MCP port).

## 2026-09-30 (stack merged)

**Done**
- PRs #3–#14 merged to `staging` in stack order; merged branches deleted (remote and local).
- `staging`: `./mvnw clean verify` green, 44 tests. Started the jar against a throw-away
  PostgreSQL: health `UP`, `/api/auth/me` 401 without a token, register 201 with the hardened
  refresh cookie, same email in capitals 409, wrong password 401. Refresh/logout were not re-run by
  hand (covered by `RefreshTokenIntegrationTest`).

**Decided**
- 2.3 Google sign-in deferred (⏸). Phase 3 (habits) is next.

## 2026-09-30 (roadmap 2.2 — `feat/user-service` fully lifted)

**Done**
- `RefreshTokenService`: 32 random bytes (base64url), stored as SHA-256 hex, 7 days
  (`app.security.refresh-token.ttl`). `consume` locks the row (`PESSIMISTIC_WRITE`), revokes it and
  returns the user id; a **revoked token presented again = copied** → every live token of that user
  is revoked (WARN log) and 401 — `noRollbackFor` so that revoke isn't rolled back by the 401.
- `POST /api/auth/refresh` (public): rotates; 401 `AUTH_INVALID_REFRESH_TOKEN` for missing, unknown,
  expired, revoked or reused. `POST /api/auth/logout` (public — the access token may have expired):
  revokes, clears the cookie, 204 even with nothing to revoke.
- Register and login now also set the cookie. The refresh token is **only** in the cookie:
  `refresh_token`, httpOnly, Secure (`app.security.refresh-token.cookie-secure`), SameSite=Strict,
  `Path=/api/auth`. CSRF stays off: the cookie can't be sent cross-site and only reaches
  `/api/auth/*`.
- Register/login/refresh are one transaction each (user + token row together).
- `RefreshTokenIntegrationTest` (7) incl. plan §4.4's loop (register → refresh → me → logout →
  refresh fails). 44 tests pass.
- With this, everything `feat/user-service` drafted is on step branches, fixed. That branch can be
  deleted once the stack is merged.

**Not done**
- No concurrency test for two refreshes racing on one token (the row lock covers it).
- Revoke-all on password change waits for a password-change endpoint (not in M1).

## 2026-09-30 (roadmap 2.1)

**Done**
- `POST /api/auth/register` (201) — creates a LOCAL user and signs straight in;
  400 `VALIDATION_FAILED` / `USER_INVALID_TIMEZONE`, 409 `USER_EMAIL_TAKEN`.
- `POST /api/auth/login` (200) — one answer, 401 `AUTH_INVALID_CREDENTIALS`, for unknown email,
  wrong password and Google-only accounts; an unknown email still spends a BCrypt check (hash of a
  random value made at startup) so timing doesn't reveal which emails exist.
- `GET /api/auth/me` — the signed-in `UserResponse`. `/api/ping` is gone.
- `AuthTokenResponse` record: `accessToken`, `tokenType` `Bearer`, `expiresIn` (900), `user`. No
  refresh token yet — 2.2 adds it as an httpOnly cookie, never in the body.
- `RegisterRequest`/`LoginRequest`/`AuthTokenResponse` mask the password/token in `toString`.
- Login failures are logged by user id or reason — never the email or password.
- `AuthIntegrationTest` (6); `SecurityIntegrationTest` now uses `/me`. 37 tests pass.

## 2026-09-30 (roadmap 1.3 — Phase 1 done)

**Done**
- New `security` package. `SecurityProperties` (`app.security.jwt.secret` from `JWT_SECRET` with
  no default, `access-token-ttl` 15m, `cors.allowed-origins` from `CORS_ALLOWED_ORIGINS`, default
  `http://localhost:3000`).
- `JwtService`: `sub` = email, `uid` = user id, iat/exp — nothing else. `@PostConstruct` refuses to
  start with a missing, unresolved (`${JWT_SECRET}`) or < 32-byte secret. Replaces the draft's
  hard-coded `SecurityConstants.SECRET` and 1-hour TTL.
- `SecurityConfig`: **one chain for every path** (the draft's `/api/**`-only chain left
  `/actuator/**` outside security); public = register, login, refresh, `/actuator/health`, Swagger,
  `/error` — the other project's routes are gone. Stateless, CSRF/basic/form/logout off, CORS from
  config, BCrypt `PasswordEncoder`.
- `JwtAuthenticationFilter` (built in `SecurityConfig`, not a `@Component`, so it doesn't also run
  as a servlet filter): bad/expired token or deleted user → stays anonymous → 401. `userId` in MDC.
- `JsonSecurityErrorHandler`: 401/403 in the `HttpResponse` shape, status set before the body
  (the draft wrote the body first and answered 403 for "not logged in").
- `AuthUser` record as the principal (ids only, hash kept out of `toString`);
  `AuthUserDetailsService` (also stops Boot's generated in-memory user).
- `GET /api/ping` (plan §5 step 3) — removed again in 2.1 in favour of `/api/auth/me`.
- `JwtServiceTest` (5), `SecurityIntegrationTest` (6). 31 tests pass.

**To run locally now:** `JWT_SECRET` (≥ 32 bytes) must be set, like `db_user_name`/`db_password`.

## 2026-09-30 (roadmap 1.2)

**Done**
- New `auth` package. `RefreshToken extends AuditModel`: `user` (lazy, indexed), `tokenHash` (hex
  SHA-256, unique — never the raw value), `expiresAt`, `revoked`; `isUsableAt(now)`.
- `RefreshTokenRepository` (package-private): `findByTokenHash`, `revokeAllForUser` (bulk update —
  for token reuse and password change).
- `RefreshTokenRepositoryIntegrationTest` (2). 20 tests pass.
- Hashing, issuing and rotation are 2.2 (`RefreshTokenService`), where they're first used.

## 2026-09-30 (roadmap 1.1)

**Done**
- New `user` package (package-by-feature). `User extends AuditModel`: `email` (unique, stored
  trimmed + lower-case), `passwordHash` (renamed from the draft's `password`; null for Google-only),
  `name`, `authProvider`, `providerId`, `timezone` (IANA, default `UTC`).
- `UserRepository` is package-private — other features go through `UserService`.
- `UserService`: `createLocalUser(email, passwordHash, name, timezone)` → 409 `USER_EMAIL_TAKEN`
  (also on a concurrent duplicate, via `saveAndFlush` + unique constraint), 400
  `USER_INVALID_TIMEZONE`; `findByEmail` (any case); `getById` → 404 `USER_NOT_FOUND`. Fixes the
  draft's `Optional`/`orElseThrow` mix-up and the missing `name`.
- `UserResponse` record: id, email, name, authProvider, timezone — never the hash.
- `UserServiceIntegrationTest` (6) on PostgreSQL, incl. audit columns (`createdBy` = `SYSTEM` at
  sign-up). 18 tests pass.

## 2026-09-30 (roadmap 0.6, 0.7 — Phase 0 done)

**Done**
- `common/AuditModel` — id + `createdBy`/`lastModifiedBy` (String, the email) + `createdAt`/
  `lastModifiedAt` (`Instant`). `@Getter` only: auditing writes them, nothing else. Not `@Data`, not
  generic — fixes the draft's `AuditModel<Long>` vs `AuditorAware<String>` mismatch. The draft's
  `dd-MM-yyyy` `@JsonFormat`s are gone (entities are never serialized).
- `configs/JpaAuditingConfig` — `@EnableJpaAuditing` (moved off the application class) and the
  auditor: signed-in email, or `SYSTEM` for no / anonymous / unauthenticated authentication (the
  draft recorded sign-ups as `anonymousUser`).
- 0.7: `ErrorCode` (~180 constants), `AppTables`, `CommonUtils`, `ModelMapper`,
  `CustomResponseException`, `PaginationArgs` are not lifted. `PaginationArgs` comes back with the
  first list endpoint (3.2), shaped by the api-conventions skill.
- `JpaAuditingConfigTest` (4). 12 tests pass.

**Next**
- `feat/foundation-base` → `staging` PR once 0.4–0.6 are merged into it.

## 2026-09-30 (roadmap 0.5)

**Done**
- `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`
  (Testcontainers 2.0.5, Boot-managed; `PostgreSQLContainer` is now in `org.testcontainers.postgresql`).
- `support/TestcontainersConfiguration` — `postgres:17-alpine` with `@ServiceConnection`; tests
  need no local database and no `db_user_name`/`db_password`.
- `support/IntegrationTest` — base class (`@SpringBootTest` + the container) so all integration
  tests share one cached context and one container.
- `contextLoads` re-enabled. 8 tests pass (needs Docker running).

**Gotcha**
- A stale `target/` from `feat/user-service` put its old `SecurityConfig` on the test classpath.
  `./mvnw clean test` after switching between far-apart branches.

## 2026-09-30 (roadmap 0.4)

**Done**
- `common/response/HttpResponse` — one record envelope: `status`, `success`, `message`,
  `errorCode`, `correlationId`, `fields`, `payload`. `status` is the enum name (`"NOT_FOUND"`);
  Jackson 3 writes `HttpStatus` as `"404 NOT_FOUND"` otherwise.
- `common/exception`: `ErrorCode` (status + default message; only generic codes so far — features
  add theirs), `ApplicationException(ErrorCode[, message])`, `GlobalExceptionHandler`
  (`@RestControllerAdvice`): validation → 400 with `fields`, unreadable/mistyped → 400, unknown
  path → 404, 405, method-security 401/403, anything else → 500 with a generic message, logged
  with the stack trace.
- `common/logging/CorrelationIdFilter` — first filter; keeps a safe client `X-Correlation-Id`,
  otherwise a UUID; MDC + response header + error body; `%X{correlationId}` in the log pattern.
- `AppConstants` moved to `common` (package-by-feature, `PLAN.md` §8a).
- The untracked `exceptionhandler/` drafts were replaced, not committed (`ResourceNotFoundException`
  → `ApplicationException` with a feature code; `CustomResponseException` unused).
- `GlobalExceptionHandlerTest` (7), standalone MockMvc, no Spring context. 7 tests pass.

## 2026-09-30 (open questions answered)

**Decided** (`PLAN.md` §8)
- Q1 package-by-feature (§8a) — code moves into it as each step lifts it over.
- Q2 fix `feat/user-service` by lifting it into steps 0.4–2.2; the branch is never merged.
- Q3 JWT `sub` = email, `uid` claim = user id.
- Q4 the user's calendar: `User.timezone` (IANA, default `UTC`).
- Q5 frequency = `DAILY` / `SPECIFIC_DAYS` / `X_TIMES_PER_WEEK`; streaks in days, or in Mon–Sun
  weeks for N-times-per-week, like Streaks and Habitify (§8b). `WEEKLY` and `CUSTOM` are gone.

## 2026-09-30 (project conventions)

**Done**
- Adopted delivery-app's working conventions — plan / roadmap / dev log, skills, git flow — without
  its multi-module layout (this stays one Maven project).
- `devhabit-implementation-plan-v2.md` moved to `docs/PLAN.md`, content unchanged; added a status
  header, §6a skills, §7 rules, §7a code and git conventions, §8 open questions, §9 risks, §10 status.
- Added `docs/ROADMAP.md` (plan §5 split into 21 small-PR steps across Phases 0–4, plus the
  pre-deploy Flyway step) and this log.
- `.claude/skills/` (domain + security, API, observability, testing) and
  `.claude/settings.local.json` permissions, both git-ignored like in delivery-app.

**Decided**
- Branch flow: phase base branches → `staging` → `main`. One roadmap step per branch.
- Lombok `@Getter/@Setter` and `@RequiredArgsConstructor`; no hand-written accessors or
  field-assigning constructors.

## 2026-09-30 (roadmap 0.3)

**Done**
- PR #2 (`feat/step-0-project-hygiene`) merged to `staging`: `spring.log` untracked and log output
  git-ignored; logback config renamed so Boot loads it and writes outside the repo; `open-in-view`
  off; app logging package fixed; jjwt added; `AppConstants` a plain constants holder;
  `contextLoads` disabled until Testcontainers.

## 2026-09-12 – 2026-09-19 (`feat/user-service`, not merged)

**Drafted**
- `User` entity + `AuthProvider`, `AuditModel`, `AuditorAwareImpl`, `HttpResponse`,
  `PaginationArgs`, `CommonUtils`, JWT service and filter, `SecurityConfig`, `AuthService`,
  `UserService`, auth request/response DTOs.

**Known problems** (from the 2026-09-19 review)
- Does not compile (`AuthService` calls classes and methods that don't exist).
- Auditor type mismatch (`AuditModel<Long>` vs `AuditorAware<String>`); `@Data` on `AuditModel`.
- JWT secret hard-coded in `SecurityConstants`; public-path list has another project's routes;
  one filter chain scoped to `/api/**` leaves `/actuator/**` outside security.
- Much copy-paste baggage from an old starter (`ErrorCode` ~180 constants, `AppTables`, unused
  `CommonUtils`, two clashing `HttpResponse` constructors).

## 2026-09-12 (roadmap 0.2)

**Done**
- PR #1 (`feat/config`): Swagger and log configuration.

## 2026-09-11 (roadmap 0.1)

**Done**
- Project set up with dependencies (Boot 4.1, Java 21, web MVC, JPA, security, OAuth2 client,
  validation, springdoc, PostgreSQL, Lombok).
