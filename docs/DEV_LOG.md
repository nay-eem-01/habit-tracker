# DevHabit — Development Log

Read this first at the start of every working session. Newest entry on top.
The step-by-step plan and overall progress are in `docs/ROADMAP.md`; decisions are in
`docs/PLAN.md`.

---

## Where we are

- **Phases 0–3 done** (2.3 Google sign-in deferred) — 16 of 26 steps. 0–2 are on `staging`;
  Phase 3 is on step branches waiting for PRs (below).
- **The core habit tracker so far:** register / login / refresh / logout, and habits — create,
  list, get, replace, archive — daily, on chosen weekdays, or N times a week. Check-ins and streaks
  (Phase 4) are next.
- **After M1:** goals, resources, levels, dashboard and AI insights are designed in `PLAN.md` §11
  (M2–M6); nothing there starts before Phase 4 is merged.
- Open PRs, in order: `docs/dev-log-after-merge` → `staging`; `docs/product-roadmap` → `staging`;
  `feat/habit-entity`, `feat/habit-create-read`, `feat/habit-update-archive` → `feat/habits-base`;
  then `feat/habits-base` → `staging`.
- Branch flow: step branch from the previous step's branch → PR into the phase's base branch →
  base PRs into `staging`. Claude commits, pushes and opens the PRs; Nayeem merges. Commits carry
  Nayeem's name only.
- Tests need Docker running (Testcontainers). 69 tests pass.
- To run locally: PostgreSQL running, and `db_user_name`, `db_password`, `JWT_SECRET` (≥ 32 bytes)
  set.

## Next up

1. Phase 4 — base `feat/checkins-base`: 4.1 `HabitLog` + check-in upsert → 4.2 logs → 4.3 streak
   calculator → 4.4 streak endpoint → 4.5 stats. "Today" in the user's timezone.
2. Then M2 Goals → M3 Resources → M4 Levels → M5 Dashboard → M6 AI (`PLAN.md` §11); confirm the
   milestone's open question (§11.6) first.
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
