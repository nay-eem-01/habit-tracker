# DevHabit — Feature Roadmap

What gets built, in order, and how far we are. The reasoning behind each item is in
`docs/PLAN.md` (the original build order is its §5). Update this file in the same PR that finishes
a step.

Each step is sized to be **one small PR** (see `PLAN.md` §7a). A step that grows past that gets
split here first.

**Branch flow:** each phase has a base branch. Each step branch is taken from the previous step's
branch and PRs into the phase base, in order; the base PRs into `staging` when the phase is done.

**Legend:** ✅ done · 🔄 in progress · ⬜ not started · ⏸ deferred

**Progress:** Phase 5 of 5 · 25 of 26 steps done (plus 2 pre-deploy steps) · Frontend: Phase F, own repo

## Where we are (2026-10-01)

- **Phases 0–2 merged to `staging`** (PRs #3–#14); Phases 3–4 merged (#23–#27); **Phase 5 done** on step branches.
- **The core habit loop works:** habits (daily, chosen weekdays, N times a week) → check-ins →
  strict streaks → 7/30-day stats, in each user's timezone. 110 tests pass.
- **M1 is feature-complete:** reminders (per-habit time in the user's timezone, minute scheduler, in-app notifications, optional email). 132 tests pass.
- **Phases 0–5 are all on `staging`.** **Next: the frontend** — Phase F below, in its own repo (`habit-tracker-web`, React + TypeScript + Vite — agreed 2026-10-01); before any shared deploy D.1/D.2.
- 2.3 Google sign-in is deferred. After M1: goals, resources, levels, dashboard, AI (`PLAN.md` §11).

---

## Phase 0 — Foundation ✅

Base branch: `feat/foundation-base` (0.1–0.3 predate the flow and went straight to `staging`).

| # | Step | Status |
|---|---|---|
| 0.1 | Project set-up with dependencies | ✅ |
| 0.2 | Swagger + log configuration — PR #1 | ✅ |
| 0.3 | Project hygiene: logs out of the repo, logback actually loads, `open-in-view=false`, jjwt added, `AppConstants` plain holder — PR #2 | ✅ |
| 0.4 | Exception handling: `HttpResponse` envelope, one `@RestControllerAdvice`, `ApplicationException` + `ErrorCode`, correlation id, no exception text in 500 bodies | ✅ |
| 0.5 | Testcontainers PostgreSQL test base; re-enable `contextLoads` | ✅ |
| 0.6 | Common base: `AuditModel` (`@Getter/@Setter`, auditor type matches), JPA auditing, auditor is the signed-in email or `SYSTEM` | ✅ |
| 0.7 | Leave the old starter's baggage behind (`ErrorCode` ~180 constants, `AppTables`, `CommonUtils`, `ModelMapper`, `CustomResponseException`) — done by not lifting it | ✅ |

## Phase 1 — Entities and JWT (plan §5 steps 2–3) ✅

Base branch: `feat/security-base`.

| # | Step | Status |
|---|---|---|
| 1.1 | `User` + `AuthProvider` + `UserRepository` + `UserService` (`passwordHash`, `providerId`, `timezone`) per `PLAN.md` §2.1 | ✅ |
| 1.2 | `RefreshToken` entity + repository, hash stored, never the raw value (§2.4) | ✅ |
| 1.3 | JWT infrastructure: `JwtService` (`sub` = email), `JwtAuthenticationFilter`, stateless `SecurityConfig` covering every path, secret from env (fail fast), 401 entry point, actuator `health` open, `GET /api/ping` behind `authenticated()` | ✅ |

## Phase 2 — Authentication (plan §5 steps 4–5) ✅ (2.3 deferred)

Base branch: `feat/auth-base`.

| # | Step | Status |
|---|---|---|
| 2.1 | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` | ✅ |
| 2.2 | `POST /api/auth/refresh` (rotate, httpOnly cookie) and `POST /api/auth/logout` (revoke); §4.4 definition of done as an integration test | ✅ |
| 2.3 | Google OAuth2 login — find, create or link by email; success handler issues app JWT + refresh cookie (§4.5) | ⏸ later — decided 2026-09-30 |

## Phase 3 — Habits (plan §5 step 6) ✅

Base branch: `feat/habits-base`.

| # | Step | Status |
|---|---|---|
| 3.1 | `Habit` entity + `FrequencyType` (`DAILY`, `SPECIFIC_DAYS`, `X_TIMES_PER_WEEK` — `PLAN.md` §8b), `frequencyConfig` as `jsonb` (Hibernate's native JSON mapping), repository scoped by `user_id` | ✅ |
| 3.2 | Create / get / list (paginated) habits | ✅ |
| 3.3 | Update, archive (soft delete), unarchive; user A cannot see or edit user B's habits (integration test) | ✅ |

## Phase 4 — Check-ins, streaks, stats (plan §5 steps 7–8) ✅

Base branch: `feat/checkins-base`. "Today" is in the user's timezone (`PLAN.md` §8 Q4); streak rules in §8b; check-in rules in §12.1.

| # | Step | Status |
|---|---|---|
| 4.0 | Record the check-in rules and the reminders plan (`PLAN.md` §12) | ✅ |
| 4.1 | `HabitLog` entity, unique `(habit_id, log_date)`; `POST /api/habits/{id}/checkin` as an upsert | ✅ |
| 4.2 | `GET /api/habits/{id}/logs` (date range, paginated) | ✅ |
| 4.3 | Streak calculator — current and longest, strict, computed on read; days for `DAILY`/`SPECIFIC_DAYS`, Mon–Sun weeks for `X_TIMES_PER_WEEK`; unit-tested per frequency type | ✅ |
| 4.4 | `GET /api/habits/{id}/streak` | ✅ |
| 4.5 | `GET /api/habits/{id}/stats` — completion rate over 7 / 30 days | ✅ |

## Phase 5 — Reminders and notifications (M1, added 2026-09-30) ✅

Base branch: `feat/reminders-base`. Design in `PLAN.md` §12.2; Q11 (channels) answered with the recommended default (in-app + SMTP email, off until configured).

| # | Step | Status |
|---|---|---|
| 5.1 | `reminderTime` on habits (set / clear, in the user's timezone) | ✅ |
| 5.2 | `notifications` table + `GET /api/notifications`, mark read, read all | ✅ |
| 5.3 | Reminder scheduler — every minute, per-user timezone, due today and not done, no duplicates | ✅ |
| 5.4 | Email channel behind `NotificationSender` (console in dev, SMTP when configured) | ✅ |

## Phase F — Frontend (own repo: `habit-tracker-web`, started 2026-10-01)

React + TypeScript + Vite SPA, TanStack Query for server state, Tailwind. Talks to this API through
the Vite dev proxy (`/api`), so the SameSite=Strict refresh cookie works; API types are generated
from the OpenAPI spec (`/v3/api-docs`). Tracked here so the whole product is in one plan; the code,
branches and PRs live in the frontend repo, same flow (phase base → step branches → `staging`).

| # | Step | Status |
|---|---|---|
| F.1 | Scaffold: Vite + TS + Tailwind + router + TanStack Query, dev proxy, API client with silent token refresh | 🔄 |
| F.2 | Sign in / register pages, protected routes, sign out | ⬜ |
| F.3 | Habit list, create / edit (schedule, target, reminder time), archive | ⬜ |
| F.4 | Today view — one-tap check-in, streak next to each habit | ⬜ |
| F.5 | Habit detail — streak, 7/30-day stats, log history | ⬜ |
| F.6 | Notification bell (unread count polling), list, mark read; reminder-time field | ⬜ |

## Before any shared deploy

| # | Step | Status |
|---|---|---|
| D.1 | Flyway back in: `ddl-auto=validate` + `V1__init_schema.sql` from the entities at that point (`PLAN.md` §3, R1) | ⬜ |
| D.2 | Production profile: no `show-sql`, no security DEBUG, Swagger off, actuator `health`/`info` only | ⬜ |

## Deferred ⏸

| Item | Returns when |
|---|---|
| Flyway | before the first shared or production deploy (D.1) |
| 2.3 Google sign-in | when Nayeem picks it up; needs a Google OAuth client id + secret |

## After M1 — goals, resources, levels, dashboard, AI

Designed in `PLAN.md` §11. **Nothing here starts before Phase 4 is merged** — the core habit tracker
comes first. Open questions Q6–Q10 (`PLAN.md` §11.6) are answered before each milestone starts.
Steps get split further if one grows past a small PR.

### M2 — Goals

| # | Step | Status |
|---|---|---|
| G.1 | `Goal` entity + `/api/goals` CRUD (paginated, `?status=`) | ⬜ |
| G.2 | Link / unlink a habit to a goal with `goalTargetDays` | ⬜ |
| G.3 | Goal progress — calculator (unit-tested) + `GET /api/goals/{id}/progress` | ⬜ |
| G.4 | Mark achieved / abandoned | ⬜ |

### M3 — Resources

| # | Step | Status |
|---|---|---|
| R.1 | `Resource` entity (`NOTE` / `LINK`) + `/api/resources` CRUD, `?goalId=`, `?type=`, `?q=` | ⬜ |
| R.2 | `GET /api/goals/{id}/resources`, pinned first | ⬜ |

### M4 — Levels

| # | Step | Status |
|---|---|---|
| X.1 | XP + level + tier calculator, unit-tested (`PLAN.md` §11.3) | ⬜ |
| X.2 | `GET /api/me/level` | ⬜ |

### M5 — Dashboard

| # | Step | Status |
|---|---|---|
| A.1 | Today + completion rates (7/30/90, change vs previous period) | ⬜ |
| A.2 | Heatmap, weekday and time-of-day patterns | ⬜ |
| A.3 | Streaks at risk, best / slipping habits, goals and level on the dashboard | ⬜ |

### M6 — AI insights

| # | Step | Status |
|---|---|---|
| I.1 | `aiInsightsEnabled` opt-in, aggregates builder, `InsightGenerator` interface with a fake | ⬜ |
| I.2 | Claude API implementation, `insights` table, `GET /latest`, `POST` (once a day) | ⬜ |

## Not planned yet

| # | Feature |
|---|---|
| L6 | Frontend — separate repo; next up after Phase 5 |
