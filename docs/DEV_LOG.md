# DevHabit — Development Log

Read this first at the start of every working session. Newest entry on top.
The step-by-step plan and overall progress are in `docs/ROADMAP.md`; decisions are in
`docs/PLAN.md`.

---

## Where we are

- **Phase 0 ✅, Phase 1 ✅, Phase 2 🔄 (2.1, 2.2 done; 2.3 Google left)** — 12 of 21 steps.
- `feat/user-service` is fully lifted and fixed into steps 0.4–2.2; don't merge it — delete it once
  the stack below is merged.
- The stack, each branch taken from the one before (PR each into its phase base, in order):
  - `feat/foundation-base` ← `feat/exception-handling` ← `test/testcontainers-base` ← `feat/common-base`
  - `feat/security-base` ← `feat/user-entity` ← `feat/refresh-token-entity` ← `feat/jwt-security`
  - `feat/auth-base` ← `feat/register-login` ← `feat/refresh-logout`
- Branch flow: step → phase base → `staging`. Claude commits and pushes; commits carry Nayeem's
  name only (no Claude attribution).
- Tests need Docker running (Testcontainers). 44 tests pass.

## Next up

1. Open and merge the PRs in stack order (see "Where we are"); each phase base → `staging`.
2. 2.3 Google OAuth2 — needs a Google Cloud OAuth client id/secret (see Open items).
3. Phase 3 — habits.

## Open items

| Item | Needs | Blocks |
|---|---|---|
| Google OAuth client (id + secret, redirect URI) from Google Cloud Console | Nayeem | 2.3 |
| Set `JWT_SECRET` (≥ 32 bytes) in the run configuration — the app no longer starts without it | Nayeem | running locally |
| `.mcp.json` (IntelliJ MCP server) is untracked — local-only or shared? | Nayeem | nothing |
| PR #2's commits are authored as `Claude <noreply@anthropic.com>`; from now on commits carry Nayeem's identity. Rewriting merged history is not worth it | — | nothing |
| Week starts on Monday for every user (`PLAN.md` §8b) — per-user week start if anyone asks | later | nothing |

---

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
