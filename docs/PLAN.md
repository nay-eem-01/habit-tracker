# DevHabit — Backend Build Plan (M0–M1)

**Status:** in progress — Phase 0 (foundation) mostly done; progress is tracked in `docs/ROADMAP.md`
**Owner:** Nayeem
**Plan version:** v2 (2026-09-12), conventions and tracking added 2026-09-30 (§6a–§10)
**Stack:** Java 21, Spring Boot 4.1, Spring MVC (blocking — NOT WebFlux), Spring Data JPA, PostgreSQL, Spring Security (JWT-based, Google OAuth2 + email/password), springdoc-openapi (Swagger UI), Lombok
**Architecture:** Single monolith, package-by-feature (not layer-by-layer, **not multi-module**)
**Scope of this document:** Project skeleton → Entities → Security/JWT → Habit CRUD → Check-in/Streaks. Goals, Resources, Analytics, and AI insights are deliberately OUT of scope here (later milestones).

How the three docs fit together:

| File | Holds | Changes when |
|---|---|---|
| `docs/PLAN.md` (this) | the *why*: settled decisions, entity design, rules, open questions | a decision is taken or reversed |
| `docs/ROADMAP.md` | the *what, in which order*: phases → steps, each one small PR, with status | a step starts or finishes |
| `docs/DEV_LOG.md` | the *where are we*: current state, next up, open items, dated entries | every working session |

---

## Changelog from v1
1. **Flyway removed for now.** Hibernate manages the schema directly in dev (`ddl-auto: update`). Re-introduce Flyway before any production/shared-environment deploy — see §3 for the tradeoff.
2. **Entity design added as its own section (§2)** — fields, types, relations, spelled out before any code is written.
3. **Auth flipped to JWT, and moved earlier in the build order.** Security infrastructure (JWT issuing/validation, stateless filter chain) is now Step 2, before Habit CRUD, not woven in alongside it. This reverses the original "session-based, not JWT" rule — flagging that reversal explicitly since it changes several downstream decisions (CORS, CSRF, token storage).

---

## 1. Guiding rules for the implementing agent (updated)

1. **No reactive stack.** `spring-boot-starter-web`, blocking JDBC via Spring Data JPA.
2. **No multi-module build.** One Maven project, package-by-feature.
3. **Streaks are never stored as a mutable counter.** Computed on read from `habit_logs`. No `current_streak` column on `habits`.
4. **Streak logic is strict**: missing any scheduled day resets the streak to 0. No grace days.
5. **One `users` table for both auth methods.** No separate OAuth/local tables.
6. **JWT-based auth, stateless sessions.** Access token (short-lived) + refresh token (longer-lived, DB-backed for revocation). `sessionCreationPolicy: STATELESS`. See §4.
7. **No schema migration tool for now.** Hibernate `ddl-auto: update` in dev. Flag before this ships anywhere beyond local dev — see §3.
8. **Do not build Goals, Resources, or AI features in this pass.** Leave `// TODO: link to Goal entity, M3` comments instead of building toward them.
9. **Every endpoint documented via springdoc annotations** as you go.

---

## 2. Entity design

Three entities for this milestone. Relations: `User 1—N Habit`, `Habit 1—N HabitLog`. A fourth, `RefreshToken`, exists purely to support JWT auth (§4) and isn't part of the domain model.

### 2.1 `User`

| Field | Type | Notes |
|---|---|---|
| `id` | `Long` (PK) | |
| `email` | `String` | unique, not null |
| `passwordHash` | `String` | nullable — null for OAuth-only users who never set a password |
| `authProvider` | `enum { LOCAL, GOOGLE }` | not null |
| `providerId` | `String` | nullable — Google's `sub` claim; null for LOCAL users |
| `name` | `String` | display name, 2–100 chars |
| `timezone` | `String` | IANA id, not null, default `UTC` — added 2026-09-30, §8 Q4 |
| `createdAt` | `Instant` | not null, default now |

Relations: `@OneToMany(mappedBy = "user")` → `List<Habit>`.

```java
@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String passwordHash; // nullable

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider authProvider;

    private String providerId; // nullable

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Habit> habits = new ArrayList<>();
}
```

### 2.2 `Habit`

| Field | Type | Notes |
|---|---|---|
| `id` | `Long` (PK) | |
| `user` | `User` (`@ManyToOne`) | FK `user_id`, not null |
| `name` | `String` | not null, e.g. max 120 chars |
| `category` | `String` | nullable |
| `frequencyType` | `enum { DAILY, SPECIFIC_DAYS, X_TIMES_PER_WEEK }` | not null — changed 2026-09-30, see §8b |
| `frequencyConfig` | JSON | e.g. `{"days":["MON","WED","FRI"]}` or `{"timesPerWeek":3}` — shape depends on `frequencyType` |
| `targetCount` | `int` | default 1 (e.g. "drink water 8x/day") |
| `isArchived` | `boolean` | default false — soft delete flag |
| `createdAt` | `Instant` | not null, default now |

Relations: `@ManyToOne` → `User`; `@OneToMany(mappedBy = "habit")` → `List<HabitLog>`.

**On `frequencyConfig` (JSON column) without Flyway:** since you're not hand-writing SQL migrations right now, don't fight Hibernate for JSONB — use the `hypersistence-utils-hibernate-63` library's `@Type(JsonType.class)` on a `Map<String, Object>` or a small dedicated `FrequencyConfig` record, and let Hibernate infer the column as `jsonb`. This keeps schema generation and the Java model in sync automatically, which is the main advantage of skipping Flyway for now.

```java
@Entity
@Table(name = "habits")
public class Habit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FrequencyType frequencyType;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> frequencyConfig;

    @Column(nullable = false)
    private int targetCount = 1;

    @Column(nullable = false)
    private boolean isArchived = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "habit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HabitLog> logs = new ArrayList<>();

    // TODO: link to Goal entity, M3
}
```

### 2.3 `HabitLog`

| Field | Type | Notes |
|---|---|---|
| `id` | `Long` (PK) | |
| `habit` | `Habit` (`@ManyToOne`) | FK `habit_id`, not null |
| `logDate` | `LocalDate` | not null |
| `completedCount` | `int` | default 0 |
| `note` | `String` | nullable, e.g. max 500 chars |
| `createdAt` | `Instant` | not null, default now |

**Unique constraint** on `(habit_id, log_date)` — one log row per habit per day; check-ins are upserts against this.

```java
@Entity
@Table(name = "habit_logs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"habit_id", "log_date"}))
public class HabitLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "habit_id", nullable = false)
    private Habit habit;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(nullable = false)
    private int completedCount = 0;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
```

### 2.4 `RefreshToken` (auth infrastructure, not domain data)

| Field | Type | Notes |
|---|---|---|
| `id` | `Long` (PK) | |
| `user` | `User` (`@ManyToOne`) | FK `user_id`, not null |
| `tokenHash` | `String` | store a hash of the token, never the raw value |
| `expiresAt` | `Instant` | not null |
| `revoked` | `boolean` | default false |
| `createdAt` | `Instant` | not null |

Why a table instead of a pure stateless refresh token: it lets you revoke on logout and on password change, which a purely stateless JWT can't do without a blocklist anyway. At this scale a DB row is simpler than a Redis blocklist.

---

## 3. On dropping Flyway

`application.yml` for now:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update   # dev only — Hibernate generates/updates schema from entities
    show-sql: false
    open-in-view: false
```

Tradeoff to be explicit about: `ddl-auto: update` is fine solo, local, early-stage — it lets the entity model in §2 be the single source of truth while it's still moving. It becomes a liability the moment more than one person touches the DB, or you deploy anywhere shared, because there's no migration history and Hibernate's auto-update can do surprising things with column type changes or renames (it won't rename a column — it'll add a new one and leave the old one orphaned). Re-introduce Flyway (`ddl-auto: validate` + `V1__init_schema.sql` generated from the then-current entity state) before that point. No action needed now — just don't forget this is a deliberate, temporary trade.

---

## 4. Security: JWT-first

### 4.1 Shape

- **Access token**: JWT, short-lived (15 min), claims: `sub` (email), `uid` (user id), issued/expiry — changed 2026-09-30, §8 Q3. Sent in `Authorization: Bearer <token>` header, validated per-request, never persisted server-side.
- **Refresh token**: opaque random string (not a JWT), longer-lived (7 days), stored **hashed** in the `refresh_tokens` table (§2.4), returned to the client as an **httpOnly, Secure, SameSite=Strict cookie** — not accessible to JS, so an XSS bug can't read it. Rotated on every use (issue a new one, revoke the old row) to limit replay window.
- Session creation policy: `STATELESS`. No `HttpSession` involved anywhere.

### 4.2 Package additions

```
config/
├── SecurityConfig.java       # stateless filter chain, permitAll list
├── JwtService.java           # issue/parse/validate access tokens
├── JwtAuthFilter.java        # OncePerRequestFilter — reads Authorization header
└── OpenApiConfig.java
auth/
├── User.java, UserRepository.java
├── RefreshToken.java, RefreshTokenRepository.java
├── AuthController.java       # register, login, refresh, logout, me
├── RegisterRequest.java / LoginRequest.java
├── CustomOAuth2SuccessHandler.java   # issues app JWT after Google login instead of a session
└── AuthProvider.java (enum: LOCAL, GOOGLE)
```

### 4.3 `SecurityConfig` reference

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // no cookie-based auth on the API itself (access token is header-only);
                                           // if the refresh cookie's exposure ever worries you, scope CSRF protection
                                           // to just POST /api/auth/refresh rather than re-enabling it globally
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh",
                                  "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler(customOAuth2SuccessHandler) // issues JWT + refresh cookie, no session
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 4.4 Endpoints (Step 2, before Habit CRUD)

- `POST /api/auth/register` — validate email uniqueness, BCrypt-hash password, `auth_provider = LOCAL`.
- `POST /api/auth/login` — verify credentials, issue access token (body) + refresh token (httpOnly cookie).
- `POST /api/auth/refresh` — reads refresh cookie, validates against `refresh_tokens`, rotates it, issues new access token.
- `POST /api/auth/logout` — revokes the current refresh token row, clears the cookie.
- `GET /api/auth/me` — resolves current user from the JWT in `SecurityContext`, 401 if absent/expired.
- **Definition of done:** register → login → call a protected endpoint with the access token → let it expire → refresh → logout → refresh fails (revoked).

### 4.5 Google OAuth2 (Step 3, still before Habit CRUD)

Same `CustomOAuth2UserService` lookup/link logic as v1 (find-or-create-or-link by email), but on success, `CustomOAuth2SuccessHandler` issues an app JWT + refresh cookie and redirects to the frontend with the access token (e.g. as a URL fragment or via a short-lived one-time code exchanged for the token) instead of relying on the container session — since the whole app is stateless now, the OAuth2 login flow's own session usage is just for the handshake with Google, not for your app's auth state afterward.

---

## 5. Build order (implement in this exact sequence)

The step-by-step version of this list — split into small-PR steps, grouped into phases, with
status — is `docs/ROADMAP.md`. This section stays as the original intent.


1. **Skeleton + Swagger + logging** — boots, connects to Postgres, `ddl-auto: update` creates tables from entities, `/swagger-ui.html` loads, `GET /actuator/health` returns 200.
2. **Entities** — `User`, `Habit`, `HabitLog`, `RefreshToken` per §2. Verify tables appear correctly via `\d` in psql before writing any endpoint.
3. **JWT infrastructure** — `JwtService`, `JwtAuthFilter`, `SecurityConfig` stateless chain, `RefreshTokenRepository`. No business endpoints yet — just prove a manually-constructed test user can get a token and hit a dummy `@GetMapping("/api/ping")` behind `authenticated()`.
4. **Auth (email/password)** — register/login/refresh/logout/me per §4.4.
5. **Auth (Google OAuth2)** — per §4.5.
6. **Habit CRUD** — `Habit` entity, `HabitRepository` (every query scoped by `user_id`), full CRUD, soft delete via `is_archived`. Verify user A cannot see/edit user B's habits.
7. **Check-in + streak calculation** — `POST /api/habits/{id}/checkin` (upsert on `(habit_id, log_date)`), `GET /api/habits/{id}/logs`, `GET /api/habits/{id}/streak` (walk backwards from today/last scheduled day; a day counts if `completedCount >= targetCount`; first uncounted scheduled day breaks the streak; longest streak computed in Java over fetched logs, not raw SQL).
8. **Basic stats** — `GET /api/habits/{id}/stats`, completion rate over last 7/30 days.

---

## 6. Explicitly deferred (do not implement yet)

- Goal entity, Resource entity, Goal↔Habit linking
- Analytics beyond basic 7/30-day completion rate
- AI-generated insights
- Reminders/notifications
- Frontend code (separate milestone)
- Flyway (re-introduce before shared/production deploy — see §3)

If you find yourself building toward any of these while implementing Steps 1–8, stop — flag it instead of proceeding. If one comes up, it goes in `ROADMAP.md` → "Later milestones" and work carries on.

---

## 6a. Project skills (`.claude/skills/`)

Five skill files encode the rules below so they are applied on every task rather than remembered.
If a rule is wrong, change the skill, not just the one call site.

**`.claude/` is git-ignored** (same as delivery-app), so the skills are local working files, not
versioned artefacts: a clone does not get them, and a rule change leaves no history. The rules they
encode are summarised in §7 and §7a, which *are* versioned — if the skills are lost, this document
is the source of truth to rebuild them from.

| Skill | Covers |
|---|---|
| `habit-tracker` | The domain: users, habits, frequency types, check-ins, strict streaks computed on read, stats; scope and what is deferred; the build order. Start here. |
| `security-checklist` | Ownership via `user_id`-scoped queries, 404-not-403, acting user from the `SecurityContext`, JWT + refresh-token rules, secrets from env, what must never be logged, validation, CORS. Ends with a per-endpoint done checklist. |
| `api-conventions` | URL rules, the `HttpResponse` envelope, status codes, stable error codes, pagination with a clamped max and a sort allowlist, DTO rules, ISO-8601 dates, OpenAPI annotations. |
| `observability` | Correlation IDs via MDC, redaction, log levels per environment, retention caps, what to log at auth and check-in events, actuator exposure. |
| `testing-and-deployment` | What must have tests (streak math, ownership, token rotation), Testcontainers against real PostgreSQL — never H2, the `ddl-auto=update` → Flyway switch, environments and config. |

## 7. Non-negotiable engineering rules

- **Every habit / log query is scoped by the acting user.** `findByIdAndUserId`, never
  fetch-then-compare. The acting user comes from the `SecurityContext` — **never** from a path
  variable, parameter or body.
- **Don't leak existence.** User A asking for user B's habit gets `404`, not `403`.
- **Streaks are computed on read** from `habit_logs` (§1.3, §1.4). No stored counter, ever.
- **One `(habit_id, log_date)` row per day.** Check-in is an upsert against that unique constraint.
- **Return DTOs, never JPA entities.** Bean Validation on every request DTO.
- **Timestamps are UTC** (`Instant`); a check-in day is a `LocalDate` in the user's calendar.
- **Every list endpoint is paginated.**
- **Secrets from the environment.** The JWT secret has no default in source; the app fails to start
  without it.
- **Refresh tokens are stored hashed**, rotated on every use, revocable (§2.4, §4.1).
- **Never log** passwords, tokens, refresh cookies or `Authorization` headers.

## 7a. Code and git conventions (added 2026-09-30, same as delivery-app)

- **Package-by-feature, one Maven module.** No multi-module build (§1.2). Layout in §8a.
- **Lombok for boilerplate.** `@Getter`/`@Setter` at class level on entities and request DTOs —
  not `@Data` on JPA entities (equals/hashCode over lazy relations is a trap). Response DTOs are
  Java `record`s. Keep a hand-written masking `toString()` on DTOs with sensitive fields.
- **Dependency injection through `@RequiredArgsConstructor`** on `private final` fields. No
  hand-written constructors that only assign fields. Config values come through a
  `@ConfigurationProperties` class (`AppProperties`), not `@Value` constructor parameters. Setup
  that needs injected fields goes in a `@PostConstruct` method.
- **Branch flow: one base branch per roadmap phase.** Each phase gets a base branch
  (`feat/auth-base` for Phase 2). Every step is its own branch, taken **from the previous step's
  branch** (so nothing conflicts), and PRs into the phase's base branch in order. When the phase is
  done, the base branch PRs into `staging`; `staging` → `main` is a release.
- **Small PRs.** One branch per step in `docs/ROADMAP.md`. A PR that touches ~20 files is split
  before review; if a step would exceed ~10–15 files, split it in the roadmap first. Each commit is
  one coherent change that builds and passes the tests on its own.
- **Commit messages:** conventional prefix — `feat:`, `fix:`, `refactor:`, `docs:`, `test:`,
  `chore:`, `build:` — with an optional scope, e.g. `feat(habit): archive instead of delete`.
- **Authorship:** commits and PRs carry Nayeem's git identity only — no `Co-Authored-By`, no
  Claude attribution. Claude may commit and push step branches; force-push, `reset --hard` and
  `git clean` stay off. PRs are opened by Nayeem on GitHub.
- **Keep the tracking docs current:** the PR that finishes a roadmap step ticks it in
  `docs/ROADMAP.md` and adds an entry to `docs/DEV_LOG.md`.

**Testing:** unit-test the streak and stats math with no Spring context (every frequency type,
gaps, today-not-yet-logged, `targetCount > 1`). Integration-test against real PostgreSQL
(Testcontainers) for ownership rules, the check-in upsert and refresh-token rotation/revocation.

---

## 8. Open questions — need a decision before the step that needs them

All five answered 2026-09-30. New questions are added here as they come up.

| # | Question | Needed by |
|---|---|---|
| 1 | ~~**Package layout**~~ — **answered 2026-09-30:** package-by-feature (§8a). Easier to manage as it grows: everything for one feature sits in one folder, and deleting or changing a feature touches one place. Code moves into the new layout as each step lifts it over — no big "move everything" PR. | ✅ |
| 2 | ~~**`feat/user-service`**~~ — **answered 2026-09-30:** fix it — its code is lifted, fixed, into steps 0.4–2.2 (one small PR each); the branch itself is never merged. | ✅ |
| 3 | ~~**JWT subject**~~ — **answered 2026-09-30:** `sub` = email (as the draft code does), plus a `uid` claim with the user id. Changes §4.1. | ✅ |
| 4 | ~~**Whose "today"**~~ — **answered 2026-09-30:** the user's calendar. `User.timezone` (IANA id, e.g. `Asia/Dhaka`, default `UTC`, set at register); "today" for check-ins and streaks is `LocalDate.now(ZoneId.of(user.timezone))`. Changes §2.1. | ✅ |
| 5 | ~~**Frequency and streaks**~~ — **answered 2026-09-30:** the user picks days of the week (§8b). | ✅ |

### 8a. Package layout (decided 2026-09-30)

```
com.nayeem.habittracker
├── common/        shared, no business logic: AuditModel, HttpResponse, exceptions, ErrorCode,
│                  GlobalExceptionHandler, CorrelationIdFilter, AppConstants
├── configs/       AppProperties, SwaggerConfig, JPA auditing
├── security/      SecurityConfig, JwtService, JwtAuthenticationFilter, user details, entry point
├── user/          User, AuthProvider, UserRepository, UserService, UserResponse
├── auth/          AuthController, AuthService, RefreshToken(+Repository, Service), auth DTOs
├── habit/         Habit, FrequencyType, repository, service, controller, DTOs   (Phase 3)
└── checkin/       HabitLog, streak calculator, stats                            (Phase 4)
```

A feature's classes stay package-private where they can. Another feature uses a feature through
its service (e.g. `auth` → `UserService`), never its repository.

### 8b. Frequency and streaks (decided 2026-09-30)

How others do it: **Streaks** and **Habitify** count "3× per week" per calendar week (the week
counts if the target was met Mon–Sun); **Loop** uses a rolling 7-day window. Specific weekdays
are counted day by day everywhere, skipping days that aren't scheduled.

We take the calendar-week model — simpler to explain and to test:

| `frequencyType` | `frequencyConfig` | A streak counts | Breaks when |
|---|---|---|---|
| `DAILY` | — | days | a day ends without `completedCount >= targetCount` |
| `SPECIFIC_DAYS` | `{"days":["MON","WED","FRI"]}` | scheduled days; other days are skipped | a scheduled day ends not done |
| `X_TIMES_PER_WEEK` | `{"timesPerWeek":3}` | weeks (Mon–Sun) | a week ends with fewer than N done days |

- This replaces §2.2's `WEEKLY` (= `SPECIFIC_DAYS` with one day) and `CUSTOM` (= `SPECIFIC_DAYS`).
- The day or week in progress never breaks a streak — it only adds once it's done.
- Weeks start on Monday for everyone for now; a per-user week start can come later.
- Still strict (§1.4): no grace days.

---

## 9. Risks

**R1 — `ddl-auto=update` (§3).** Deliberate and temporary. It becomes a liability the moment the DB
is shared or deployed. Re-introduce Flyway (`validate` + `V1__init_schema.sql`) before any deploy —
it is on the roadmap as a step, not a someday.

**R2 — Testcontainers not in place yet.** `contextLoads` is disabled until it lands, so nothing
today proves the app starts against PostgreSQL. It is step 0.5, before any entity work.

**R3 — Spring Boot 4.1 is new.** Security, springdoc and jjwt examples online are mostly Boot 3.
Expect API differences (e.g. `spring-boot-starter-webmvc`, Jackson 3 vs jjwt's Jackson 2); check
the reference docs rather than assuming.

---

## 10. Where the project stands (2026-09-30)

**On `staging`** (PRs #1–#14): skeleton, Swagger, logging; Phase 0 (error envelope, correlation
ids, Testcontainers, auditing); Phase 1 (`User` with timezone, `RefreshToken`, JWT security over
every path); auth 2.1–2.2 (register, login, me, refresh with rotation and reuse detection, logout).
Everything `feat/user-service` drafted, fixed. 44 tests on real PostgreSQL.

**Deferred:** 2.3 Google sign-in (§4.5) — picked up later.

**Next:** Phase 3 (habits). Details: `ROADMAP.md`.
