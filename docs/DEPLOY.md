# AveryDay — Deployment (beta now, production later)

**AveryDay — A very Day.** This is how the app goes live with **no card, no domain, $0**, and how it moves
from beta to production. `docs/LAUNCH_GUIDE.md` has the background (Gmail app password, Google client,
VAPID). This file is the plan to follow. Free-tier limits change, so check each provider's pricing page
when you sign up.

## The setup

| Part | Where | Address |
|---|---|---|
| Web app | Vercel Hobby (free, no card) | `https://averyday.vercel.app` (the one address users see) |
| API | Render free web service, Docker | `https://averyday-api.onrender.com`, reached through Vercel |
| Database | Supabase free | Postgres 17, region near your users |
| Email | Gmail app password, `averydayhabits@gmail.com` | ~500 recipients a day |
| Push | Web Push, VAPID | — |
| Uptime alerts and keep-awake | UptimeRobot free | pings `/actuator/health` every 5 min |
| Backups | GitHub Actions, `.github/workflows/backup.yml` | encrypted daily dump, kept 30 days |

Pick the names you want. If `averyday` is taken, every step below uses the name you got instead.

**Why it fits:** Vercel sends `/api/*` on to Render, so the browser sees one site and the
`SameSite=Strict` sign-in cookie works. The UptimeRobot ping every 5 minutes keeps Render from sleeping,
so the minute-by-minute reminder scheduler keeps running. One always-on free Render service uses about
744 of the 750 free hours a month, so run **only one**. The image was checked in a 512 MB container
against an empty Postgres 17 (2026-10-13): it starts in about 9 s, runs at about 340 MB, peaked at 383 MB under load, and was never
killed. No JVM flags are needed.

## 0. Branches and environments

```
feature/fix branch ──PR──▶ staging ──PR──▶ production
                            │                 │
                            ▼                 ▼
                     beta (deploys now)  live (later, same services)
```

- `staging` is the beta. Render and Vercel deploy it automatically after CI passes.
- `production` is created when the beta is good (§10). From then on the same Render service, Vercel
  project and database follow `production`. The free tier can't host two always-on APIs, so staging is
  then tested locally (or on a paid second service, if you get one).
- `main` is retired. Leave it as it is. Delete it once `production` exists, if you like.
- The web repo (`habit-tracker_web`) uses the same branches and the same rules.

## 1. GitHub policies (both repos, before anything deploys)

Both repositories are **public**. Anything committed is visible to anyone, forever, including in history.

**Settings → General**
- [ ] Default branch: **staging**. PRs then target it, and scheduled workflows (backups) run from it.
- [ ] Pull requests: allow merge commits; turn on "Automatically delete head branches".

**Settings → Rules → Rulesets → New branch ruleset** "protect releases", Active, targets `staging` and
`production` (add `production` once it exists):
- [ ] Restrict deletions
- [ ] Block force pushes
- [ ] Require a pull request before merging: required approvals **0** (a solo developer can't approve
      their own PR), and require conversation resolution
- [ ] Require status checks to pass: **`test`** (the CI job). The backend repo has it; the web repo
      needs its own CI before this can be required there.
- [ ] Bypass list: empty, so nobody can push straight to these branches
- [ ] By convention only: `production` takes PRs from `staging` only. A hotfix goes to `staging`
      first, then gets promoted.

**Settings → Code security**
- [ ] Dependabot alerts and Dependabot security updates: on
- [ ] Secret scanning and **push protection**: on (free for public repos). A commit containing a key
      is then refused.

**Settings → Actions → General**
- [ ] Actions permissions: allow actions created by GitHub only (the workflows use `actions/*`)
- [ ] Workflow permissions: **Read repository contents** (the workflows also say `contents: read`)
- [ ] Fork pull request workflows: require approval for all outside contributors

**Settings → Secrets and variables → Actions** (backend repo only; §8):
`BACKUP_DATABASE_URL`, `BACKUP_PASSPHRASE`. The app's own secrets live **only in Render**, never in
GitHub.

**Your accounts:** two-factor authentication on GitHub and on the app's Google account. Sign in to
Render, Vercel, Supabase and UptimeRobot **with GitHub**, so one 2FA guards them all.

A public repo disables scheduled workflows after 60 days without a commit. If backups stop, re-enable
them under Actions.

## 2. Secrets: make them once, keep them in a password manager

| Secret | How | If lost or changed |
|---|---|---|
| `JWT_SECRET` | `openssl rand -base64 48` | everyone is signed out |
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` | `npx web-push generate-vapid-keys` (**new** for beta; never reuse the laptop's) | every browser's push subscription silently stops working |
| `DB_PASSWORD` | Supabase generates it (§3) | reset it in Supabase, then update Render |
| `SPRING_MAIL_PASSWORD` | Gmail app password (LAUNCH_GUIDE §2) | revoke it in Google, make a new one |
| `BACKUP_PASSPHRASE` | `openssl rand -base64 32` | the old backups can't be opened |

Rules:
- Secrets go in the password manager, then Render's environment. They never go into the repo, an issue,
  a PR, a chat or a screenshot.
- `.env` exists only on your laptop, for development, and is git-ignored. Production values never go in
  it.
- A secret that leaked (committed, pasted, shown on screen): **rotate it first**, then clean up. Push
  protection makes this rare.

## 3. Supabase (database)

1. `supabase.com` → sign in with GitHub → New project `averyday`. Let it **generate** the database
   password and save it. Pick the region nearest your users (e.g. Singapore for South Asia), and use
   the **same region for Render**.
2. **Turn off the Data API**: Project Settings → Data API → disable it (or remove `public` from the
   exposed schemas). The app connects with plain Postgres. Left on, Supabase serves the tables over
   HTTP. Flyway's tables have no row-level security, so one leaked project key would expose every user.
   Afterwards, Advisors → Security should show no "RLS disabled" error you can't explain.
3. Connect → **Session pooler** (works over IPv4). From it:
   - `DB_URL=jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require`
   - `DB_USERNAME=postgres.<project-ref>`
   - `DB_PASSWORD=<the saved password>`
   - and for backups, the same in libpq form:
     `postgresql://postgres.<project-ref>:<password>@<pooler-host>:5432/postgres?sslmode=require`
4. Leave the `public` schema **empty**. Flyway builds it from V1 on the first start. The prod profile
   refuses to start on a non-empty schema with no Flyway history; it doesn't skip V1. (Found by this
   check: baselining a non-empty schema skipped V1 and failed at V2.)

## 4. Render (API)

1. `render.com` → sign in with GitHub → New → **Web Service** → `nay-eem-01/habit-tracker`.
   - Name `averyday-api`, region = Supabase's, branch **staging**, runtime **Docker** (it finds the
     `Dockerfile`), instance type **Free**.
   - Health check path: `/actuator/health`.
   - Auto-Deploy: **After CI checks pass**.
2. Environment variables (copy each value from the password manager):

   | Variable | Value |
   |---|---|
   | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | §3 |
   | `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `5` (the free pooler has few connections) |
   | `JWT_SECRET` | §2 |
   | `APP_FRONTEND_URL` | `https://averyday.vercel.app` (no trailing `/`) |
   | `CORS_ALLOWED_ORIGINS` | `https://averyday.vercel.app` |
   | `APP_DISPLAY_NAME` | `AveryDay` (also the default now) |
   | `APP_NOTIFICATIONS_EMAIL_ENABLED` | `true` |
   | `APP_NOTIFICATIONS_EMAIL_FROM`, `SPRING_MAIL_USERNAME` | `averydayhabits@gmail.com` |
   | `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | `smtp.gmail.com`, `587` |
   | `SPRING_MAIL_PASSWORD` | the Gmail app password |
   | `APP_PUSH_ENABLED` | `true` |
   | `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` | §2 |
   | `VAPID_SUBJECT` | `mailto:averydayhabits@gmail.com` |
   | `GOOGLE_CLIENT_ID` | §6 |
   | `APP_FILES_ENABLED` | `false` (Render's disk is wiped on every deploy) |

   The image already sets `SPRING_PROFILES_ACTIVE=prod`. Don't add `JAVA_TOOL_OPTIONS`: it isn't needed,
   and the image's `-XX:MaxRAMPercentage=75` would override it anyway.
3. First deploy: the log should show `Successfully applied 18 migrations` and `Started`.
   `https://averyday-api.onrender.com/actuator/health` shows `{"status":"UP"}`.

## 5. Vercel (web app, `habit-tracker_web`)

1. Add `vercel.json` to the web repo, on `staging`, by PR. Use the real Render address:
   ```json
   {
     "rewrites": [
       { "source": "/api/:path*", "destination": "https://averyday-api.onrender.com/api/:path*" },
       { "source": "/(.*)", "destination": "/index.html" }
     ],
     "headers": [
       { "source": "/(.*)", "headers": [
         { "key": "X-Content-Type-Options", "value": "nosniff" },
         { "key": "X-Frame-Options", "value": "DENY" },
         { "key": "Referrer-Policy", "value": "strict-origin-when-cross-origin" },
         { "key": "Permissions-Policy", "value": "camera=(), microphone=(), geolocation=()" }
       ] }
     ]
   }
   ```
   Files that exist (`/sw.js`, `/privacy.html`, assets) are served before the rewrites apply. Vercel
   adds HTTPS and HSTS itself. A Content-Security-Policy is left for later: Google's sign-in script needs
   it tuned and tested.
2. `vercel.com` → sign in with GitHub → Add New → Project → `habit-tracker_web`. Framework Vite. Project
   name `averyday`.
3. Settings → Git → **Production Branch: `staging`** (later `production`). Preview deployments get
   other addresses, so Google sign-in and cookies only work on the main address. That's fine.
4. Environment variable `VITE_GOOGLE_CLIENT_ID` (§6). It's public by design; no other variables.
5. Before the first deploy, the frontend shows "AveryDay", not "DevHabit": page title, manifest name
   and short name, and the service worker's default notification title.

## 6. Google sign-in

In the Google Cloud project (LAUNCH_GUIDE §4), signed in as `averydayhabits@gmail.com`:
- Branding: app name **AveryDay**, support email `averydayhabits@gmail.com`, home page
  `https://averyday.vercel.app`, privacy policy `https://averyday.vercel.app/privacy.html`.
- Client → Authorized JavaScript origins: add `https://averyday.vercel.app`.
- Audience: stay in **Testing** for the beta and add each beta tester's Gmail as a test user (up to
  100). Publish when you open sign-ups.
- The same client id goes to Render `GOOGLE_CLIENT_ID` and Vercel `VITE_GOOGLE_CLIENT_ID`.

## 7. UptimeRobot (alerts, and keeps the API awake)

`uptimerobot.com` → sign in → two HTTP monitors, every 5 minutes, alerts to your own email:
1. `https://averyday-api.onrender.com/actuator/health`. This one also stops Render from sleeping.
2. `https://averyday.vercel.app`

## 8. Backups

`.github/workflows/backup.yml` runs daily at 21:30 UTC. It dumps the `public` schema with `pg_dump`,
then gzips and encrypts it with `BACKUP_PASSPHRASE` (AES-256). It keeps the result **30 days** as a
workflow artifact. The repo is public, so the encryption is what keeps the dump private.

1. Add the two Actions secrets (§1): `BACKUP_DATABASE_URL` (the libpq URL, §3) and
   `BACKUP_PASSPHRASE`.
2. Actions → Database backup → **Run workflow**. It must go green and produce an artifact.
3. **Restore test, once, before inviting anyone.** Download the artifact, unzip it, then:
   ```bash
   docker run -d --name restore-test -e POSTGRES_PASSWORD=pw postgres:17
   gpg -d backup.sql.gz.gpg | gunzip | docker exec -i restore-test psql -U postgres -q
   docker exec restore-test psql -U postgres -c "select count(*) from users"
   docker rm -f restore-test
   ```
   The count matches the live one, so the backup works. To restore for real, restore into an empty
   database the same way, then point `DB_URL` at it.

The dump-encrypt-decrypt round trip was checked locally on 2026-10-13.

## 9. Deploy-day checklist

Day 1, setup (about 3 hours):
- [ ] §1 GitHub policies on both repos
- [ ] §2 secrets made and saved
- [ ] §3 Supabase, Data API off
- [ ] §4 Render on `staging`, health `UP`
- [ ] §5 frontend renamed, `vercel.json` merged, Vercel on `staging`
- [ ] §6 Google origin and test users, §7 UptimeRobot, §8 backup run plus restore test

Day 2, check everything on `https://averyday.vercel.app` (desktop Chrome **and** a phone):
- [ ] Sign up with email → the verification mail arrives (check spam, mark "not spam") → the link works
- [ ] Sign out, sign in. Sign in with Google (as a test user).
- [ ] Forgot password → the mail arrives → reset works → the old session is signed out
- [ ] Create a habit, check in, the streak and the dashboard look right, a goal works
- [ ] Notifications on → reminder set 2 minutes ahead → a push arrives with the tab closed. On an
      iPhone, add the app to the home screen first; iOS sends web push only to installed apps.
- [ ] Settings: change timezone. Export data downloads JSON. Delete a test account, and it's gone.
- [ ] Wrong password 6 times for one email → the 6th answers "too many attempts" (5 failures per 15 min)
- [ ] `https://averyday-api.onrender.com/v3/api-docs` and `/swagger-ui.html` are 404 (prod profile)
- [ ] Admin: sign up **a separate account**, e.g. `averydayhabits+admin@gmail.com` (the mail
      arrives in the same inbox). Supabase SQL editor:
      `update users set role = 'ADMIN' where email = 'averydayhabits+admin@gmail.com';`
      → `/admin` works, and the user app answers that account with 403. Until the web app's A.5 is built,
      use this account only on `/admin`.
- [ ] Render logs: no `ERROR` on start; reminders tick every minute without errors
- [ ] Then invite 5–10 testers (and add them as Google test users)

## 10. Promoting beta to production (later)

When the beta has run cleanly for a while:
1. Run the backup workflow by hand, so there's a fresh dump.
2. Create the branch once: `git switch staging && git pull && git switch -c production && git push -u
   origin production`. Later promotions are a PR **staging → production**, after CI passes. Add
   `production` to the ruleset (§1). Do the same in the web repo.
3. Render → Settings → Branch: `production`. Vercel → Production Branch: `production`. Same services,
   same database, so beta users keep their data.
4. Re-run the §9 day-2 checks that touch what changed.
5. Before opening sign-ups to the public:
   - Publish the Google app (out of Testing).
   - Fix the shared-IP rate limits (below).
   - Get a domain plus a transactional mail provider (SPF/DKIM), and move off Vercel Hobby once you
     charge money.

**Rollback:** Render → Deploys → pick the last good one → Rollback. This is safe only because
migrations only **add** (columns, tables): code one release back still runs on the newer schema. Keep it
that way. Drop or rename something only in a later release, once no running code uses it. A broken
migration isn't rolled back; it's fixed forward with a new `V{n}`. Restoring the backup is the last
resort, because it loses everything since the dump.

## 11. Known limits of the free setup (accepted for the beta)

- **Rate limits by IP are shared.** Requests reach Render through Vercel, so the API sees Vercel's
  address, not the user's. The per-IP limits (e.g. 10 sign-ups an hour) then apply to everyone behind
  the same Vercel egress address. That's fine for 10 testers and not for a public launch. The
  per-email login lock still works per user. Fix it before going public (same-site domain on one host,
  or a proxy that passes the client IP with a shared secret).
- **Vercel Hobby is for non-commercial use.** It's fine while Pro is free. Move before you charge.
- A deploy or a Render restart pauses the scheduler for about a minute; a reminder due in that minute is
  skipped.
- Gmail: a ~500/day limit and no SPF/DKIM of your own, so the first mails may go to spam. Tell testers.
- Supabase free has no point-in-time recovery; the daily dump is the backup. At most a day of data can
  be lost.
- Render's free build minutes are limited. Merge to `staging` in batches, not every small PR.
