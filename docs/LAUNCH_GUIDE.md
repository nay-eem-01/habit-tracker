# Launch Guide — what to set up, in order, for $0

Everything here is free, needs no domain, and is done once. Do the steps in order: later ones use
values from earlier ones. Write every value you get into a password manager (or a private note), never
into git.

| # | Step | Gives you | Time |
|---|---|---|---|
| 1 | Create the app's Gmail account | the app's email identity | 10 min |
| 2 | Gmail app password | email sending (reset, verification) | 5 min |
| 3 | Choose a host and get its address | the URL users open | 30–60 min |
| 4 | Google OAuth client id | "Sign in with Google" | 15 min |
| 5 | VAPID keys | web push reminders | 2 min |
| 6 | Fill in the environment variables and deploy | a running app | 30 min |
| 7 | Checks after deploy | confidence it works | 10 min |

---

## 1. The app's Gmail account

Create one new Gmail account just for the app, e.g. `devhabit.app@gmail.com` (use the final name
once chosen). It is used for: the Google Cloud project, the OAuth consent screen's support email, sending
email (step 2), the VAPID contact (step 5), and the hosting account (step 3).

- Turn on **2-Step Verification** (Google Account → Security). Step 2 needs it.
- Don't use your personal account: if the app's mail is ever flagged as spam, your own inbox stays safe.

## 2. Email sending: Gmail app password (instead of Brevo)

No domain and no phone OTP from a third party needed. Limit: about 500 recipients a day — plenty to start.

1. Signed in as the app account: Google Account → Security → 2-Step Verification → **App passwords**
   (or open `myaccount.google.com/apppasswords`).
2. Name it `habit-tracker`, create, copy the 16-character password (spaces don't matter).
3. These become the environment variables:
   ```
   APP_NOTIFICATIONS_EMAIL_ENABLED=true
   APP_NOTIFICATIONS_EMAIL_FROM=devhabit.app@gmail.com
   SPRING_MAIL_HOST=smtp.gmail.com
   SPRING_MAIL_PORT=587
   SPRING_MAIL_USERNAME=devhabit.app@gmail.com
   SPRING_MAIL_PASSWORD=<the 16-character app password>
   MAIL_SMTP_AUTH=true
   ```
4. Later, with a domain and more users, move to a transactional provider (Brevo, Resend, SES). Only
   these variables change; no code.

## 3. Hosting — pick A, or B if A won't accept your card

The web app and the API **must be on the same site** (the sign-in cookie is `SameSite=Strict`). Both
options below keep them on one address.

### Option A (recommended): Oracle Cloud "Always Free" VM — everything on one machine

Why: an always-on VM (Ampere ARM, at least 2 CPUs / 12 GB RAM on the free tier — check the current
Always Free page), a disk that survives restarts (so file uploads can be switched back on), no sleeping,
no time limit. The minute-by-minute reminder scheduler needs an always-on server.

1. Sign up at `cloud.oracle.com/free` with the app account. Oracle asks for a **card to verify identity**
   (a small hold, refunded; Always Free resources are never charged). Pick a **home region** close to
   your users — Always Free ARM capacity is tied to it and can't be changed.
2. Create a VM: Compute → Instances → Create. Image **Ubuntu 24.04**, shape **VM.Standard.A1.Flex**
   (2 OCPU, 12 GB). If it says "out of capacity", try again later or another availability domain.
   Save the SSH key it offers.
3. Networking: in the VM's subnet security list, allow inbound TCP **80** and **443**. On the VM:
   `sudo iptables -I INPUT -p tcp -m multiport --dports 80,443 -j ACCEPT && sudo netfilter-persistent save`.
4. A free address: sign in at `duckdns.org` (with the app's Google account), create a subdomain, e.g.
   `devhabit.duckdns.org`, and set it to the VM's public IP.
5. On the VM install Docker (`curl -fsSL https://get.docker.com | sh`) and git. In `~/app/`: clone this
   repository as `~/app/habit-tracker`, copy the frontend's `dist/` folder there (`npm run build` in
   `habit-tracker-web`), and add these two files:

   `docker-compose.yml`
   ```yaml
   services:
     db:
       image: postgres:17-alpine
       environment:
         POSTGRES_DB: habit-tracker
         POSTGRES_USER: app
         POSTGRES_PASSWORD: ${DB_PASSWORD}
       volumes: [db:/var/lib/postgresql/data]
       restart: unless-stopped
     api:
       build: ./habit-tracker          # or an image you push
       env_file: .env
       environment:
         DB_URL: jdbc:postgresql://db:5432/habit-tracker
         DB_USERNAME: app
         APP_FILES_DIR: /data/files
       volumes: [files:/data/files]
       depends_on: [db]
       restart: unless-stopped
     web:
       image: caddy:2
       ports: ["80:80", "443:443"]
       volumes:
         - ./Caddyfile:/etc/caddy/Caddyfile
         - ./dist:/srv
         - caddy:/data
       restart: unless-stopped
   volumes: {db: {}, files: {}, caddy: {}}
   ```

   `Caddyfile` (Caddy gets the HTTPS certificate by itself)
   ```
   devhabit.duckdns.org {
       handle /api/* {
           reverse_proxy api:8080
       }
       handle /actuator/health {
           reverse_proxy api:8080
       }
       handle {
           root * /srv
           try_files {path} /index.html
           file_server
       }
   }
   ```
6. `.env` next to them (step 6), then `docker compose up -d --build`.
7. Backups: a daily `pg_dump` to the VM's disk is the minimum; copy it off the machine weekly (e.g. to
   the app's Google Drive). Example cron line:
   `0 3 * * * docker compose -f ~/app/docker-compose.yml exec -T db pg_dump -U app habit-tracker | gzip > ~/backups/$(date +\%F).sql.gz`

### Option B: no card — Vercel (web) + Render (API) + Supabase (database)

Works, with limits: Render's free API has 512 MB RAM, **sleeps after 15 idle minutes** (about a minute
to wake) and loses its disk on every restart (file uploads stay off). Render's own free Postgres is
deleted after 30 days, so the database lives on Supabase's free plan instead.

1. **Supabase** (`supabase.com`, sign in with GitHub): new project → **Connect** → the **Session
   pooler** string (it works over IPv4, the direct one may not). Gives `DB_URL`, `DB_USERNAME`
   (`postgres.<project-ref>`), `DB_PASSWORD`; write the URL as
   `jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require`. A free project pauses after a week
   without activity — the reminder scheduler keeps it active.
2. **Render** (`render.com`): New → Web Service → this repo → it finds the `Dockerfile`. Instance type
   Free. Environment variables from step 6, plus `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70`. You get
   `https://<name>.onrender.com`.
3. Keep it awake (reminders run every minute): a free job at `cron-job.org` that GETs
   `https://<name>.onrender.com/actuator/health` every 10 minutes. One always-on free service uses
   ~744 of the 750 free hours a month — run only this one.
4. **Vercel** (`vercel.com`): import `habit-tracker-web`. Add `vercel.json` to that repo so `/api` goes
   to the API through Vercel (same site for the cookie):
   ```json
   { "rewrites": [
       { "source": "/api/:path*", "destination": "https://<name>.onrender.com/api/:path*" },
       { "source": "/(.*)", "destination": "/index.html" } ] }
   ```
   The app's address is `https://<project>.vercel.app`.
5. Rate limits by IP are weaker here: requests reach the API through two proxies, and the API can't
   tell real client addresses apart reliably. The per-email login lock still works.

### Not recommended

- **AWS:** new accounts get credits that end after 6 months; after that it's paid.
- Fly.io, Railway: trials or credits, not lasting free tiers.

## 4. Google OAuth client id ("Sign in with Google")

Only the **client id** is needed — no secret, no redirect URL (the app uses Google's ID-token flow).

1. Open `console.cloud.google.com` signed in as the app account. Top bar → project picker → **New
   project**, name it after the app.
2. Menu → **Google Auth Platform** (older consoles: APIs & Services → OAuth consent screen) →
   **Get started**:
   - App name: the app's name. User support email: the app account.
   - Audience: **External**.
   - Contact email: the app account. Agree, create.
3. **Data access / Scopes:** nothing to add — sign-in uses only `openid`, `email` and `profile`, which
   need no Google review.
4. **Clients** (older: Credentials → Create credentials → OAuth client ID) → **Create client**:
   - Application type: **Web application**.
   - Authorized JavaScript origins — add each, exactly (scheme + host + port, no path, no `/` at the end):
     - `http://localhost:5173` and `http://localhost` (local development)
     - your live address from step 3, e.g. `https://devhabit.duckdns.org` or `https://<project>.vercel.app`
   - Authorized redirect URIs: leave empty.
   - Create, and copy the **Client ID** (`…apps.googleusercontent.com`).
5. **Audience → Publish app** (out of "Testing"). While in Testing, only listed test users can sign in.
   With only the basic scopes, publishing needs no verification. If Google asks for a homepage and
   privacy-policy URL, use pages on your live address (a simple `/privacy` page in the web app).
6. Give the id to both sides: backend `GOOGLE_CLIENT_ID=<id>` (used when roadmap step 2.3 is built),
   frontend `VITE_GOOGLE_CLIENT_ID=<id>`. It isn't secret — it's in every page that shows the button.

## 5. VAPID keys (web push)

On any machine with Node: `npx web-push generate-vapid-keys`. It prints a public and a private key.
```
APP_PUSH_ENABLED=true
VAPID_PUBLIC_KEY=<public key>
VAPID_PRIVATE_KEY=<private key — secret>
VAPID_SUBJECT=mailto:devhabit.app@gmail.com
```
Generate them once. New keys later would silently invalidate every browser's subscription.

## 6. Environment variables

All names are in `.env.example`. For production:

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` (the Docker image sets it) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | from step 3 (Option A: `DB_PASSWORD` is yours to choose) |
| `JWT_SECRET` | `openssl rand -base64 48` — keep it; changing it signs everyone out |
| `APP_FRONTEND_URL` | the live address, no trailing `/` |
| `CORS_ALLOWED_ORIGINS` | the live address |
| `APP_DISPLAY_NAME` | the app's name (used in emails) |
| mail variables | step 2 |
| push variables | step 5 |
| `APP_FILES_ENABLED` | `true` on Option A (disk survives), `false` on Option B |
| `GOOGLE_CLIENT_ID` | step 4 (once 2.3 is built) |

## 7. Checks after deploy

1. `https://<address>/actuator/health` shows `{"status":"UP"}`; `/v3/api-docs` is 404 (prod profile).
2. Register with a real address you own → the confirmation email arrives (check spam the first time;
   mark "not spam").
3. Forgot password → the reset email arrives and the link works.
4. Allow notifications in the browser, set a habit's reminder to two minutes from now → a push arrives.
5. Sign in on a phone, "Add to home screen" → the app opens full-screen.
6. Wrong password 6 times → the 6th answers "too many attempts" (rate limits are on).
