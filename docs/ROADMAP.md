# DevHabit — Roadmap

The steps, in order. Each step is one small PR. Decisions behind them are in `docs/PLAN.md`.

**Legend:** ✅ done · 🔄 in progress · ⬜ not started · ⏸ waiting

**Branch flow:** each phase has a base branch; each step branches from the previous step and PRs
into the base; the base PRs into `staging`.

## Done

| Milestone | What it gave | PRs |
|---|---|---|
| Phases 0–2 | foundation, JWT auth, register/login/refresh/logout, forgot/reset/change password | #1–#14, #66–#68 |
| Phases 3–5 (M1) | habits, check-ins, strict streaks, 7/30-day stats, reminders, in-app notifications | #17–#32 |
| M2 Goals | goals, habit links, progress, achieved/abandoned | #41–#45 |
| M3 Resources | notes, links, file uploads on local disk | #46–#56 |
| M4 Levels | XP, levels, tiers | #57–#59 |
| M5 Dashboard | today, completion trends, highlights, patterns | #60–#63 |
| D.1, D.1b | Flyway; logging with masking and retention | #64–#65, #69 |
| Frontend F.1–F.6 | in `habit-tracker-web` | that repo |

## Phase 6 — Clean-up and fixes (base `feat/cleanup-base`)

| # | Step | Status |
|---|---|---|
| 6.1 | Docs: plan rewritten (overview, decisions, architecture), roadmap and dev log compacted | ✅ |
| 6.2 | Logging: OpenTelemetry out (`[cid=…]` stays), async appenders block instead of drop; unused deps and config out | ✅ |
| 6.3 | A done day keeps the target it was logged with (review C1) | ✅ |
| 6.4 | A schedule change starts a new streak and keeps the XP earned (review C1) | ✅ |
| 6.5 | Reminders: one failing habit doesn't stop the others; injected clock for refresh tokens; one-query mark-all-read (C2–C4); timezones must be region names | ✅ |

## Phase 7 — Security and launch (base `feat/launch-base`)

| # | Step | Status |
|---|---|---|
| 7.1 | Production profile; forwarded headers; daily clean-up of expired tokens | ✅ |
| 7.2 | Rate limits on public auth endpoints (per IP) and failed logins (per email) | ⬜ |
| 7.3 | Passwords limited to 72 bytes (BCrypt) | ⬜ |
| 7.4 | Email: sent off the request thread; reminders no longer emailed; free SMTP set-up documented | ⬜ |
| 7.5 | File uploads behind `app.files.enabled` (off) | ⬜ |
| 7.6 | Dockerfile and CI (tests on every PR) | ⬜ |
| D.3 | Deploy: host, TLS, managed Postgres + backups, same-site domains | ⏸ host not chosen |

## Phase 8 — Accounts (base `feat/accounts-base`)

| # | Step | Status |
|---|---|---|
| 8.1 | Email verification (one-time tokens shared with password reset) | ⬜ |
| 8.2 | Edit profile: name, timezone, promotional-email opt-in | ⬜ |
| 8.3 | Delete account (everything goes) | ⬜ |
| 8.4 | Export my data (JSON) | ⬜ |
| 8.5 | Delete a habit for good | ⬜ |
| 2.3 | Google sign-in, ID-token flow, links only to verified accounts | ⏸ client id |

## Phase 9 — Habits (base `feat/habits-plus-base`)

| # | Step | Status |
|---|---|---|
| 9.1 | Units on counted habits ("8 glasses") | ⬜ |
| 9.2 | Quit habits (clean days, a check-in is a slip) | ⬜ |
| 9.3 | Rest days with weekly limit and XP cost; spendable XP balance | ⬜ |
| 9.4 | Free plan limits: 7 active habits, 2 active goals | ⬜ |

## Phase 10 — Web push (base `feat/push-base`)

| # | Step | Status |
|---|---|---|
| 10.1 | Push subscriptions + VAPID; reminders sent as web push | ⬜ |

## Later

| Item | Waits for |
|---|---|
| Phase 11 — auto check-ins from integrations (GitHub first) | decision (PLAN §6 Q15) |
| File uploads back on, on S3-compatible storage (R2) | host (Q16) |
| Pro plan and billing | users asking for it |
| M6 AI insights (opt-in, aggregates only) | after Phase 11 |
| Frontend: PWA, push, new screens | `habit-tracker-web` |
