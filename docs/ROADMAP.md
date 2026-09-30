# DevHabit — Feature Roadmap

What gets built, in order, and how far we are. The reasoning behind each item is in
`docs/PLAN.md` (the original build order is its §5). Update this file in the same PR that finishes
a step.

Each step is sized to be **one small PR** (see `PLAN.md` §7a). A step that grows past that gets
split here first.

**Branch flow:** each phase has a base branch. Each step branch is taken from the previous step's
branch and PRs into the phase base, in order; the base PRs into `staging` when the phase is done.

**Legend:** ✅ done · 🔄 in progress · ⬜ not started · ⏸ deferred

**Progress:** Phase 0 of 4 · 3 of 21 steps done (plus 2 pre-deploy steps)

## Where we are (2026-09-30)

- Skeleton, Swagger, logging and project hygiene are on `staging` (PRs #1, #2).
- `feat/user-service` holds a first draft of the user entity, JWT and auth service. It does not
  compile and is too big for one PR — its pieces move into steps 0.4–1.3 (`PLAN.md` §8 Q2).
- Decide `PLAN.md` §8 Q1 (package layout) before step 1.1.

---

## Phase 0 — Foundation 🔄

Base branch: `feat/foundation-base` (0.1–0.3 predate the flow and went straight to `staging`).

| # | Step | Status |
|---|---|---|
| 0.1 | Project set-up with dependencies | ✅ |
| 0.2 | Swagger + log configuration — PR #1 | ✅ |
| 0.3 | Project hygiene: logs out of the repo, logback actually loads, `open-in-view=false`, jjwt added, `AppConstants` plain holder — PR #2 | ✅ |
| 0.4 | Exception handling: one `@RestControllerAdvice`, `ApplicationException` + `ResourceNotFoundException`, error codes, no exception text in 500 bodies | 🔄 drafted, untracked |
| 0.5 | Testcontainers PostgreSQL test base; re-enable `contextLoads` | ⬜ |
| 0.6 | Common base: `AuditModel` (`@Getter/@Setter`, auditor type matches), `HttpResponse` (one constructor), `PaginationArgs`, actuator `health` open | ⬜ |
| 0.7 | Remove copy-paste baggage lifted from the old starter (unused `ErrorCode`, `AppTables`, `CommonUtils` methods, `ModelMapper`) as each piece moves over | ⬜ |

## Phase 1 — Entities and JWT (plan §5 steps 2–3)

Base branch: `feat/security-base`.

| # | Step | Status |
|---|---|---|
| 1.1 | `User` + `AuthProvider` + `UserRepository` (`passwordHash`, `providerId`) per `PLAN.md` §2.1 | ⬜ |
| 1.2 | `RefreshToken` entity + repository, hash stored, never the raw value (§2.4) | ⬜ |
| 1.3 | JWT infrastructure: `JwtService`, `JwtAuthFilter`, stateless `SecurityConfig`, secret from env (fail fast), 401 entry point, `GET /api/ping` behind `authenticated()` | ⬜ |

## Phase 2 — Authentication (plan §5 steps 4–5)

Base branch: `feat/auth-base`.

| # | Step | Status |
|---|---|---|
| 2.1 | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` | ⬜ |
| 2.2 | `POST /api/auth/refresh` (rotate, httpOnly cookie) and `POST /api/auth/logout` (revoke); §4.4 definition of done as an integration test | ⬜ |
| 2.3 | Google OAuth2 login — find, create or link by email; success handler issues app JWT + refresh cookie (§4.5) | ⬜ |

## Phase 3 — Habits (plan §5 step 6)

Base branch: `feat/habits-base`.

| # | Step | Status |
|---|---|---|
| 3.1 | `Habit` entity + `FrequencyType`, `frequencyConfig` as `jsonb` (hypersistence-utils), repository scoped by `user_id` | ⬜ |
| 3.2 | Create / get / list (paginated) habits | ⬜ |
| 3.3 | Update, archive (soft delete), unarchive; user A cannot see or edit user B's habits (integration test) | ⬜ |

## Phase 4 — Check-ins, streaks, stats (plan §5 steps 7–8)

Base branch: `feat/checkins-base`. `PLAN.md` §8 Q4 (timezone) before 4.1, Q5 before 4.3.

| # | Step | Status |
|---|---|---|
| 4.1 | `HabitLog` entity, unique `(habit_id, log_date)`; `POST /api/habits/{id}/checkin` as an upsert | ⬜ |
| 4.2 | `GET /api/habits/{id}/logs` (date range, paginated) | ⬜ |
| 4.3 | Streak calculator — current and longest, strict, computed on read; unit-tested per frequency type | ⬜ |
| 4.4 | `GET /api/habits/{id}/streak` | ⬜ |
| 4.5 | `GET /api/habits/{id}/stats` — completion rate over 7 / 30 days | ⬜ |

## Before any shared deploy

| # | Step | Status |
|---|---|---|
| D.1 | Flyway back in: `ddl-auto=validate` + `V1__init_schema.sql` from the entities at that point (`PLAN.md` §3, R1) | ⬜ |
| D.2 | Production profile: no `show-sql`, no security DEBUG, Swagger off, actuator `health`/`info` only | ⬜ |

## Deferred ⏸

| Item | Returns when |
|---|---|
| Flyway | before the first shared or production deploy (D.1) |

## Later milestones (not in M0–M1)

From `PLAN.md` §6. Nothing here is started before Phase 4 is done.

| # | Feature |
|---|---|
| L1 | Goals, and linking habits to goals (M3 — `// TODO` left in `Habit`) |
| L2 | Resources |
| L3 | Analytics beyond 7/30-day completion rate |
| L4 | AI-generated insights |
| L5 | Reminders / notifications |
| L6 | Frontend |
