# DevHabit — Development Log

Read this first at the start of every working session. Newest entry on top.
The step-by-step plan and overall progress are in `docs/ROADMAP.md`; decisions are in
`docs/PLAN.md`.

---

## Where we are

- **Phase 0 (foundation)**, 3 of 7 steps done. Skeleton, Swagger, logging and hygiene are on
  `staging` (PRs #1, #2).
- `feat/user-service` (a draft of user/JWT/auth, ~31 files) does not compile and is not merged.
- Branch flow: step branch from the previous step's branch → PR into the phase's base branch
  (Phase 0: `feat/foundation-base`) → base PRs into `staging` when the phase is done.
  Claude commits and pushes; commits carry Nayeem's name only (no Claude attribution).

## Next up

1. Decide `PLAN.md` §8 Q1 (package-by-feature vs the current layers) and Q2 (`feat/user-service`).
2. 0.4 — exception handling (the untracked `exceptionhandler/` files, finished and committed).
3. 0.5 — Testcontainers, re-enable `contextLoads`.
4. 0.6 — common base, lifted from `feat/user-service` and fixed on the way.

## Open items

| Item | Needs | Blocks |
|---|---|---|
| Package layout: by-feature (plan §1.2) or keep layers (`PLAN.md` §8 Q1) | Nayeem | 1.1 |
| `feat/user-service`: split into steps instead of merging (`PLAN.md` §8 Q2) | Nayeem | 0.6, 1.x |
| Untracked `exceptionhandler/` files import `common.responses.HttpResponse`, which exists only on `feat/user-service` — they don't compile on `staging` alone; and the `Exception` handler returns `ex.getMessage()` in a 500 body | Claude, in 0.4 | 0.4 |
| JWT `sub`: user id (plan §4.1) or email (draft code) (`PLAN.md` §8 Q3) | Nayeem | 1.3 |
| Whose calendar is "today" for check-ins (`PLAN.md` §8 Q4) | Nayeem | 4.1 |
| Streaks for `X_TIMES_PER_WEEK` / `CUSTOM` (`PLAN.md` §8 Q5) | Nayeem | 4.3 |
| `.mcp.json` (IntelliJ MCP server) is untracked — local-only or shared? | Nayeem | nothing |
| PR #2's commits are authored as `Claude <noreply@anthropic.com>`; from now on commits carry Nayeem's identity. Rewriting merged history is not worth it | — | nothing |

---

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
