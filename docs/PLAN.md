# DevHabit — Backend Build Plan (M0–M1)

**Status:** in progress — Phase 0 (foundation) mostly done; progress is tracked in `docs/ROADMAP.md`
**Owner:** Nayeem
**Plan version:** v2 (2026-09-12), conventions and tracking added 2026-09-30 (§6a–§10)
**Stack:** Java 21, Spring Boot 4.1, Spring MVC (blocking — NOT WebFlux), Spring Data JPA, PostgreSQL, Spring Security (JWT-based, Google OAuth2 + email/password), springdoc-openapi (Swagger UI), Lombok
**Architecture:** Single monolith, package-by-feature (not layer-by-layer, **not multi-module**)
**Scope of this document:** M1 — Project skeleton → Entities → Security/JWT → Habit CRUD → Check-in/Streaks. Goals, Resources, Levels, the analytics dashboard and AI insights are designed in §11 and built **after** M1 (M2–M6).

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
8. **Do not build Goals, Resources, or AI features in this pass.** Leave `// TODO: link to Goal entity, M2` comments instead of building toward them. Their design is §11.
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

**Changed 2026-09-30 (roadmap 3.1):** Hibernate 7 maps JSON natively (`@JdbcTypeCode(SqlTypes.JSON)`) — no hypersistence-utils; `frequencyConfig` is a typed `FrequencyConfig` record, and the boolean is `archived` (not `isArchived`). The paragraph below is the original v2 text.

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

    // TODO: link to Goal entity, M2 (PLAN.md §11)
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

- Goal entity, Resource entity, Goal↔Habit linking — M2, M3 (§11)
- Levels / XP / tiers — M4 (§11)
- Analytics beyond basic 7/30-day completion rate — M5 (§11)
- AI-generated insights — M6 (§11)
- Reminders/notifications — moved into M1 as Phase 5 on 2026-09-30 (§12)
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

**On `staging`** (PRs #1–#14): skeleton, error format, correlation ids, Testcontainers, auditing,
`User` with timezone, JWT security, register / login / me / refresh / logout.

**Built, waiting for PRs:** Phase 3 (habits: create, list, get, replace, archive) and Phase 4
(check-ins, logs, strict streaks, 7/30-day stats). The core loop works end to end. 110 tests on
real PostgreSQL.

**Deferred:** 2.3 Google sign-in (§4.5).

**Next:** Phase 5 — reminders and notifications (§12.2), then M2–M6 (§11). Details: `ROADMAP.md`.

---

## 11. After M1 — goals, resources, levels, dashboard, AI (added 2026-09-30)

The **core habit tracker (M1: Phases 3–4) comes first**; nothing below starts before Phase 4 is
merged. Each milestone is its own set of small-PR steps in `ROADMAP.md`. Suggested order — each one
builds on the one before:

| Milestone | What the user gets | Depends on |
|---|---|---|
| **M2 Goals** | "I want to achieve X"; X has habits; a progress bar that grows as I check in | M1 |
| **M3 Resources** | notes and links kept next to a goal | M2 |
| **M4 Levels** | XP for every done day, bonuses for streaks and achieved goals, levels and tiers | M1 (M2 for the goal bonus) |
| **M5 Dashboard** | one screen to see my patterns: heatmap, best/worst weekdays, trends, streaks | M1, M4 |
| **M6 AI insights** | plain-language observations and one suggestion, from the dashboard numbers | M5 |

Same rules as M1 throughout: everything scoped by the acting user (404 for others'), DTOs not
entities, paginated lists, computed-on-read where the plan already says so (§1.3).

### 11.1 M2 — Goals

A goal is what you want to reach; habits are the daily actions that get you there.

| `Goal` field | Type | Notes |
|---|---|---|
| `user` | `User` | owner |
| `title` | `String` | ≤ 120, e.g. "Run a half marathon" |
| `description` | `String` | ≤ 2000, nullable |
| `targetDate` | `LocalDate` | nullable — "by when" |
| `status` | `ACTIVE` / `ACHIEVED` / `ABANDONED` | `ACHIEVED` is set by the user (Q7) |
| `achievedAt` | `Instant` | nullable |

**Link:** `Habit.goal` — nullable `@ManyToOne` (one goal per habit, Q6) — plus
`Habit.goalTargetDays` (nullable int): how many done days make this habit "built" for the goal,
e.g. 60.

**Progress (computed on read, never stored — same rule as streaks):**
- per habit: `min(doneDays since linked / goalTargetDays, 1)`, a done day being
  `completedCount >= targetCount`;
- per goal: the average over its habits, 0–100 %; with no habits, 0 %.
- It only ever goes up, so a missed day slows you down instead of taking progress away; streaks
  (strict) are where missed days hurt.

**API:** `/api/goals` CRUD (list paginated, `?status=`), `PUT /api/habits/{id}/goal`
(link, with `goalTargetDays`) / `DELETE` (unlink), `GET /api/goals/{id}/progress` (total + per habit).
Archiving a habit keeps its link; abandoning a goal unlinks nothing.

### 11.2 M3 — Resources

Things you keep for a goal: notes, articles, videos, books.

| `Resource` field | Type | Notes |
|---|---|---|
| `user` | `User` | owner |
| `goal` | `Goal` | nullable — a resource can stand alone |
| `type` | `NOTE` / `LINK` | files later (Q9) |
| `title` | `String` | ≤ 200 |
| `body` | `String` (`text`) | Markdown note, ≤ 20 000 chars, nullable for a link |
| `url` | `String` | `http(s)` only, ≤ 2048, required for `LINK` |
| `pinned` | `boolean` | shown first |

**API:** `/api/resources` CRUD, paginated, `?goalId=`, `?type=`, `?q=` (title contains);
`GET /api/goals/{id}/resources`. Rendering Markdown is the frontend's job; the API stores text.
Never fetch a user-supplied URL server-side (that's an SSRF hole) — link previews, if ever, go
through an allowlist.

### 11.3 M4 — Levels

Consistency gets rewarded. **XP is derived from what already exists** (habit logs, streaks, goals) —
no XP column that can drift, and a level can be recomputed any time.

| Event | XP |
|---|---|
| a done day on a habit | +10 |
| … while that habit's streak is ≥ 7 | +5 extra (consistency bonus) |
| streak reaches 7 / 30 / 100 / 365 | +50 / +200 / +500 / +1500, once per streak run |
| goal marked `ACHIEVED` (M2) | +500 |

- **Level** `n + 1` needs `50 · n · (n + 1)` total XP: level 2 at 100, 3 at 300, 4 at 600, 10 at 4 500
  (corrected 2026-10-06 — the draft said 4 950, which doesn't fit the formula or the other examples).
  Early levels come fast, later ones need weeks of consistency.
- **Tiers** group levels: Bronze 1–4 · Silver 5–9 · Gold 10–19 · Platinum 20–34 · Diamond 35+.
- **No XP is ever lost** (Q8, confirmed 2026-10-06): breaking a streak stops the bonuses; it doesn't
  take anything back.
- **Which days earn XP** (settled in X.1, 2026-10-06): the days the streak counts — scheduled done
  days; for N-times-a-week at most N done days a week. A check-in on an unscheduled day earns
  nothing (as for streaks and rates, §12.1). The calculator walks the same units as
  `StreakCalculator` (`StreakCalculator.walk`), so the two can't disagree.
- **N-times-a-week habits** count in weeks: the bonus applies from a 1-week run, milestones at
  1 / 4 / 14 / 52 weeks (≈ 7 / 30 / 100 / 365 days).
- A milestone is paid each time a run reaches it, so a new run after a break earns it again.
- XP is recomputed from logs, so **correcting** history (undoing a check-in within the 7-day window,
  raising a habit's target) can lower it — that's a correction, not a streak penalty.
- **API:** `GET /api/me/level` → `xp`, `level`, `tier`, `xpForNextLevel`, `progressToNextLevel`.
  Level-ups become a notification type once Phase 5 exists (§12.2).
- One calculator class, pure Java, unit-tested like the streak calculator. At one user's scale
  (≈ 10 habits × 365 days) computing from logs on read is cheap; cache only if measurement says so.

### 11.4 M5 — Dashboard and pattern analysis

`GET /api/dashboard` — one call for the home screen, all computed from logs in the user's timezone:

- **Today:** each active habit, scheduled or not, done or not.
- **Completion rate:** 7 / 30 / 90 days, overall and per habit, with the change against the
  previous period.
- **Heatmap:** per-day completion ratio for the last 365 days (GitHub-style).
- **Weekday pattern:** completion rate by weekday — "Mondays are your weakest day".
- **Time of day:** when check-ins happen (from `HabitLog.createdAt`), in the user's timezone.
- **Streaks:** current and longest per habit; habits at risk (scheduled today, not done, streak ≥ 3).
- **Best / most-slipping habits:** highest rate; biggest drop against last period.
- **Goals and level:** progress bars (M2) and level/tier (M4).

Heavy parts may become separate endpoints (`/api/dashboard/heatmap`) if the payload grows.

**Settled in A.1 (2026-10-06):**
- Only **active** habits are on the dashboard (archived ones keep their XP, M4, but aren't "today").
- **Due today** follows the reminder rule, now shared as `habit.DueRules`: daily always, chosen
  weekdays on them, N-a-week until the week's N is met — a habit done today stays due today.
  `today.due` / `today.done` count those, for "N of M done".
- **Previous period** = the same number of days just before the window; it's over, so every day in
  it counts. Clipped to the habit's first day; `null` rate and `change` when nothing was expected.
- **Overall rate** adds the habits up but caps each at what it was asked for, so extra check-ins on an
  N-a-week habit don't hide another habit's missed days. `change` is in rate points (0.12 = +12 %).
- Numbers come from `StatsCalculator` / `StreakCalculator`, so the dashboard matches each habit page.

### 11.5 M6 — AI insights

The dashboard shows the numbers; AI turns them into plain language and one suggestion
("You complete Reading 90 % on weekends but 40 % on weekdays — try moving it to the morning").

- The server builds a **compact JSON of aggregates from M5** (rates, weekday pattern, streaks, goal
  progress) and sends that to the Claude API. **Never** notes, resource text or the email — unless
  the user opts in (Q10). Habit names are sent (they're what makes the insight useful), so the
  user switches the feature on explicitly: `User.aiInsightsEnabled`, default off.
- Output: 3–5 observations + 1 suggestion, stored in an `insights` table (user, period, text,
  model, createdAt). `GET /api/insights/latest`; `POST /api/insights` regenerates, at most once a
  day per user (it costs money).
- API key from the environment (`ANTHROPIC_API_KEY`), never committed; the call sits behind an
  `InsightGenerator` interface with a fake for tests, so no test calls the real API. The model is
  chosen when M6 starts.
- Failure (timeout, quota) → the dashboard still works; insights are an extra, never a blocker.

### 11.6 Open questions for M2–M6

Recommendations are in bold; confirm or change them before the milestone starts.

| # | Question | Needed by |
|---|---|---|
| 6 | Can one habit serve **one goal (recommended — simpler, no double counting)** or several? | M2 |
| 7 | Goal progress: **target done-days per habit (recommended, §11.1)**, or "done / scheduled days until the target date"? And is a goal at 100 % achieved automatically, or **does the user confirm (recommended)**? | M2 |
| 8 | Levels: **XP never lost (recommended)**, or does breaking streaks cost XP? | M4 — **answered 2026-10-06: never lost** |
| 9 | Resources: **notes + links first (recommended)**; file uploads (S3) later? | M3 |
| 10 | AI: **opt-in, aggregates + habit names only (recommended)**; allow notes with a second opt-in? | M6 |

---

## 12. Check-in rules and reminders (added 2026-09-30)

### 12.1 Check-ins (Phase 4)

- **`POST /api/habits/{id}/checkin`** sets the day's **absolute** `completedCount` (not +1), so a
  retried request can't double-count. Body: `date` (default: today in the user's timezone),
  `completedCount` (default: the habit's `targetCount`, i.e. "mark done"; `0` undoes), `note`.
- It's an upsert on `(habit_id, log_date)` done in one SQL statement (`INSERT … ON CONFLICT DO
  UPDATE`), so two taps at once still leave exactly one row.
- **Allowed dates:** not in the future (user's today); not before the habit was created; at most
  **7 days back** — enough to catch up on a missed evening, not enough to rewrite a streak's
  history. (Decided as a default 2026-09-30; easy to change.)
- An archived habit can't be checked in (409 `HABIT_ARCHIVED`); its history stays readable.
- **Streak units:** days for `DAILY` / `SPECIFIC_DAYS` (unscheduled days are skipped; a check-in on
  an unscheduled day stays in the logs but counts for neither the streak nor the rate — corrected
  in 4.5, it would push a rate past 100 %); Mon–Sun weeks for
  `X_TIMES_PER_WEEK`. Today — or this week — never breaks a streak while it's still in progress,
  and the week the habit was created never breaks one either (it may be a partial week).
- **Stats** (7 / 30 days): `done` scheduled days ÷ `expected` scheduled days in the window, with the
  window clipped to the habit's first day and today counted only once it's done. For
  `X_TIMES_PER_WEEK`, `expected` = `timesPerWeek × days / 7`, and the rate is capped at 100 %.

### 12.2 Reminders and notifications (Phase 5, M1 — moved in 2026-09-30)

Reminders keep the habit alive; they belong in the core tracker, not after it.

- **Per habit:** `reminderTime` (local time, e.g. `07:30`, nullable = no reminder), in the user's
  timezone. One time per habit in M1.
- **Scheduler:** every minute, finds habits whose reminder time is this minute *in each user's own
  timezone* (done in SQL: `(now() at time zone users.timezone)`), that are due today, not archived
  and not yet done — and creates a reminder. A unique `(habit_id, date, type)` stops duplicates
  after a restart or with two app instances. Minutes missed while the app is down are not re-sent.
- **In-app notifications:** a `notifications` table (user, type, title, body, habit, `readAt`),
  `GET /api/notifications` (paginated, unread first), `POST /api/notifications/{id}/read`,
  `POST /api/notifications/read-all`. The frontend polls it — works with no extra accounts.
- **Delivery channels** behind a `NotificationSender` interface: in-app always; **email** next
  (Spring Mail; console sender in dev, SMTP when configured — same shape as delivery-app);
  **web/mobile push** once there is a frontend (Q11).
- Later types on the same table: "streak at risk" (evening, streak ≥ 3, not done), level-ups (M4),
  goal achieved (M2), weekly AI insight ready (M6).

| # | Question | Needed by |
|---|---|---|
| 11 | Reminder channels: **in-app + email (recommended)** now, push when a frontend exists? Which email provider — **SMTP (e.g. a Gmail app password) to start (recommended)**? | 5.4 — **answered 2026-10-01 with the recommendation** (email off until configured; no per-user opt-out yet) |


---

## 13. File uploads — R.3 design (2026-10-05; Q12–Q14 answered with the recommendations)

M3 adds a third resource type, **`FILE`**: a PDF, an image or a text file kept next to a goal, the
same way a note or link is. Q12–Q14 were answered on 2026-10-05 with the recommendations
(Nayeem: "go with the recommendations"); the options are kept below for the record.

### 13.1 What taskatask-backend does (looked at 2026-10-05)

`taskatask-common/…/fileobject` is a generic file module the whole app shares:

- **`FileObject` record, separate from the feature** — `originalName`, generated `name`,
  `uploadType`, `fileExtension`, `mimeType` (found by Apache Tika from the bytes, not trusted from the
  client), `fileKey`, `fileSize`, `bucket`, `isPublic`, `filePublicUrl`. A feature entity (e.g.
  `CostDocument`) points at it.
- **Two-step upload:** `POST /api/file/upload?type=TASK_IMAGE` (multipart) stores the bytes and returns
  the `FileObject`; the client then sends that `fileId` in the feature's own request (`addDocument
  { fileId }`).
- **S3 through Spring Cloud AWS `S3Template`.** Key = `[public/]<type folder>/<random name>`; the
  user's file name never becomes the key. `FileUploadType` → folder; a privacy matrix decides public
  vs private.
- **Downloads:** a pre-signed GET URL valid 10 minutes (main path), or bytes streamed through the
  server as `attachment` (fallback).
- **Delete:** the S3 object, then the `FileObject`, then the feature row.

**Worth copying:** file metadata kept apart from the feature entity; type found from the bytes;
random storage keys; downloads as `attachment`; a storage service the features call instead of S3.

**Not worth copying (problems for us):**

| In taskatask | Why it matters here |
|---|---|
| `FileObject` has **no owner**; `/api/file?fileIds=…`, `/as-resource` and `DELETE /api/file` act on any id for any signed-in user | breaks our rule that every read and write checks ownership (404 for someone else's) |
| Upload and attach are separate calls; nothing removes an uploaded file that is never attached | orphan files pile up forever, and need a clean-up job to fix |
| Tika's result is recorded but **never checked against an allowlist**; limit 256 MB | anything can be uploaded, HTML and SVG included |
| Whole file read into a `byte[]` in memory | fine for small files, not at 256 MB |
| `Cache-Control: public, immutable, 1 year` on private downloads | shared caches may keep a private file |
| S3 delete and row delete aren't coordinated | a failure halfway leaves a dangling row or a lost file |
| WebFlux / reactive | we're on blocking Spring MVC (the stack line at the top) |

Separately: taskatask's `application-development.properties` and `application-staging.properties`
contain an AWS access key and secret in git. Those should be rotated there. Here, credentials only ever
come from the environment (security-checklist).

### 13.2 Decisions to take

**Q12 — Where the bytes live**

| Option | For | Against |
|---|---|---|
| A. **Local disk** (`app.files.dir`) behind a `FileStorage` interface | no account or cost, simple to test (`@TempDir`), right for one server with a persistent disk | lost on hosts with an ephemeral disk (most PaaS); one server only |
| B. **S3-compatible** (AWS S3 / Cloudflare R2 / MinIO) behind the same interface | survives redeploys, scales, pre-signed URLs take the download load off the app | needs a bucket and credentials now; tests need a MinIO container |
| C. PostgreSQL `bytea` | one backup, transactional with the row | bloats the database and its backups; poor for anything large |

**Recommended: A now, B as its own step when the deploy target is chosen** (with D.1/D.2). Both
sit behind `FileStorage` (`put`, `open`, `delete`), so the switch touches no feature code.

**Q13 — Limits and types**

**Recommended:**
- **10 MB per file**, **100 MB per user** (sum of stored sizes, checked before saving).
  `spring.servlet.multipart.max-file-size=10MB`, `max-request-size=11MB`.
- **Allowed:** PNG, JPEG, WebP, GIF, PDF, plain text (`.txt`, `.md`). The type is detected from the
  bytes (Tika core, magic bytes) and **must** be on the list. The extension must match it.
- **Not allowed:** SVG and HTML (can carry scripts), Office files (macros), archives, executables.
  Add types when there's a reason.
- Answers: too big → **413 `FILE_TOO_LARGE`**, type not allowed → **415 `FILE_TYPE_NOT_ALLOWED`**,
  quota full → **413 `FILE_QUOTA_EXCEEDED`**.

**Q14 — Upload flow**

| Option | For | Against |
|---|---|---|
| A. **One multipart request creates the resource**: `POST /api/resources/files` with `file` + `title`, `body`, `goalId`, `pinned` | no orphans, no clean-up job, ownership set at creation | a generic upload endpoint for other features (avatars) is a separate endpoint later |
| B. Two-step like taskatask: `POST /api/files` → `fileId`, then `POST /api/resources {type: FILE, fileId}` | one upload endpoint reused by every feature | orphans need a clean-up job; a `fileId` must be checked as owned *and* unused |

**Recommended: A.** It's the right size for one feature. If a second feature needs files, the
`StoredFile` table and `FileStorage` are already shared, and only a new endpoint is needed.

### 13.3 Design (with the recommendations)

- **`StoredFile` entity** (`files` package), separate from `Resource` like taskatask's `FileObject`,
  **but owned**: `user`, `storageKey` (random UUID, never the user's name — no path traversal),
  `originalName` (cleaned, ≤ 255), `contentType` (the detected one), `sizeBytes`, `sha256`,
  audit columns. `Resource` gets `file` (one-to-one, nullable). `ResourceType.FILE`.
- **Rules for a `FILE` resource:** `file` required, `url` not allowed, `body` an optional comment.
  `PUT` changes title, body, goal and pinned only — a different file means a new resource. JSON
  `POST /api/resources` with `type: FILE` → 400 (files go through the multipart endpoint).
- **Upload order:** check size → detect type → check quota → write the bytes → insert rows. If the
  insert fails, delete the bytes again. Streamed to storage from Spring's temp file; never a
  whole-file `byte[]`.
- **Download:** `GET /api/resources/{id}/file`, owner only (someone else's → 404). Headers:
  `Content-Type` = the stored type, `Content-Disposition: attachment; filename*=UTF-8''…`,
  `X-Content-Type-Options: nosniff`, `Cache-Control: private, no-store`. The frontend keeps the access
  token in memory, so a plain `<a href>` can't send it. It downloads with `fetch` and saves or
  previews the blob. (A short-lived signed link can be added with S3, Q12 B.)
- **Delete:** deleting a `FILE` resource deletes its rows; the bytes are deleted **after commit**
  (`@TransactionalEventListener(AFTER_COMMIT)`, like `NotificationDispatcher`). If that fails, the
  orphan is logged by storage key (never the file name). A sweeper can come later if it ever happens.
- **Resource responses** gain `file: { name, contentType, sizeBytes }` for `FILE` resources; list
  filters (`?type=FILE`, `?goalId=`, `?q=`) work unchanged.
- **Abuse / viruses:** single-user scale, so **no virus scanner for now**. The protection is the
  allowlist, attachment-only downloads with `nosniff`, random keys and the quota. Before sign-up
  opens to strangers, add ClamAV (`clamd` container) or a hosted scanner as its own step.
- **Logging:** file id, size and detected type only — never the original file name or content
  (observability skill). `StoredFile` and requests mask the name in `toString`.

### 13.4 Steps (replace R.3 in the roadmap)

| # | Step |
|---|---|
| R.3a | `StoredFile` entity + `FileStorage` interface + local-disk implementation + type detection and allowlist (Tika core) + limits config and the per-user quota (`FileService.store`); unit and integration tests |
| R.3b | `ResourceType.FILE`; `POST /api/resources/files` (multipart), multipart-limit → 413, `file` in responses; JSON create with `FILE` → 400 |
| R.3c | `GET /api/resources/{id}/file` download headers; delete removes the bytes after commit; ownership tests on both |
| R.3d | S3-compatible `FileStorage` (MinIO via Testcontainers), switched by config — when the deploy target is known, next to D.1/D.2 |

| # | Question | Needed by |
|---|---|---|
| 12 | Storage: **local disk now behind `FileStorage`, S3-compatible when deploying (recommended)**, S3 straight away, or Postgres `bytea`? | R.3a — **answered 2026-10-05 with the recommendation** |
| 13 | Limits: **10 MB per file, 100 MB per user; images + PDF + text only (recommended)**? | R.3a — **answered 2026-10-05 with the recommendation** |
| 14 | Upload flow: **one multipart call creates the resource (recommended)**, or two-step upload-then-attach like taskatask? | R.3b — **answered 2026-10-05 with the recommendation** |
